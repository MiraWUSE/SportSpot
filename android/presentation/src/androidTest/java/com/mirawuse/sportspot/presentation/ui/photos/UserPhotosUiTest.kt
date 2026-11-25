package com.mirawuse.sportspot.presentation.ui.photos

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.location.GeoPoint
import com.mirawuse.sportspot.domain.model.photo.MAX_USER_PHOTO_BYTES
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoStatus
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.domain.model.spot.SportType
import com.mirawuse.sportspot.domain.model.spot.Spot
import com.mirawuse.sportspot.domain.model.spot.SpotId
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import com.mirawuse.sportspot.presentation.R
import com.mirawuse.sportspot.presentation.ui.details.SpotDetailsScreen
import com.mirawuse.sportspot.presentation.ui.home.HomeUiState
import com.mirawuse.sportspot.presentation.ui.theme.SportSpotTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class UserPhotosUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val alice = "11111111-1111-4111-8111-111111111111"
    private val bob = "22222222-2222-4222-8222-222222222222"
    private val id = "33333333-3333-4333-8333-333333333333"
    private val target = PhotoTarget(PhotoTargetType.SPOT, "osm:way:7")

    @Test
    fun ownPendingStatusDisappearsWhenTheAccountChanges() {
        val auth = FakeAuth(profile(alice))
        val photos = FakePhotos(PhotoSubmission(id, alice, target, PhotoStatus.PENDING, null))
        val model = PhotosViewModel(compose.activity.applicationContext, auth, photos)
        compose.setContent {
            SportSpotTheme(darkTheme = true) {
                val state by auth.authState.collectAsState()
                val owner = state as? AuthState.Authenticated
                owner?.let { PhotoEditor(it.profile.id, target.type.limit, model, target) }
            }
        }
        compose.onNodeWithText(label(R.string.photos_choose)).assertIsDisplayed()
        compose.onNodeWithText(label(R.string.photos_pending)).assertIsDisplayed()
        compose.runOnIdle { auth.authState.value = profile(bob) }
        compose.onNodeWithText(label(R.string.photos_pending)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.photos_choose)).assertIsDisplayed()
    }

    @Test
    fun publicGalleryRefreshesAfterApprovalAndUsesPublicImageAccess() {
        val auth = FakeAuth(AuthState.Guest)
        val photos = FakePhotos(PhotoSubmission(id, alice, target, PhotoStatus.PENDING, null))
        val model = PhotosViewModel(compose.activity.applicationContext, auth, photos)
        compose.setContent {
            SportSpotTheme(darkTheme = false) { PhotoGallery(target, model) }
        }
        compose.onNodeWithText(label(R.string.photos_empty_gallery)).assertIsDisplayed()
        compose.onNodeWithContentDescription(label(R.string.photos_preview)).assertDoesNotExist()
        compose.runOnIdle { photos.approved = true }
        compose.onNodeWithText(label(R.string.photos_refresh)).performClick()
        val previewLabel = label(R.string.photos_preview)
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription(previewLabel).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(previewLabel).assertIsDisplayed()
        assertNull(photos.lastPreviewOwner)
    }

    @Test
    fun proposalHasOneDisclosureForAllPhotosAndNoDisclosureWhenEmpty() {
        val item = PhotoSubmission(id, alice, PhotoTarget.proposal(bob), PhotoStatus.PENDING, null)
        val second = item.copy(id = alice, status = PhotoStatus.APPROVED)
        val images = CompletableDeferred<List<PhotoSubmission>>()
        val photos = FakePhotos(item)
        var failed = false
        photos.readQueue = { proposalId ->
            when {
                proposalId == bob -> images.await()
                !failed -> {
                    failed = true
                    throw PhotoException(PhotoError.OFFLINE)
                }
                else -> emptyList()
            }
        }
        val model = PhotosViewModel(compose.activity.applicationContext, FakeAuth(profile(alice, UserRole.MODERATOR)), photos)
        compose.setContent {
            SportSpotTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                    Column(Modifier.width(300.dp).verticalScroll(rememberScrollState())) {
                        Box(Modifier.testTag("with-photos")) { ProposalPhotoEvidence(alice, bob, model) }
                        Box(Modifier.testTag("without-photos")) { ProposalPhotoEvidence(alice, alice, model) }
                    }
                }
            }
        }
        val first = hasAnyAncestor(hasTestTag("with-photos"))
        val other = hasAnyAncestor(hasTestTag("without-photos"))
        val check = hasText(label(R.string.moderation_check_attachments))
        val hide = hasText(label(R.string.photos_hide_preview))
        val show = hasText(label(R.string.photos_show_preview))
        compose.runOnIdle { assertEquals(emptyList<String?>(), photos.queueRequests) }
        compose.onNode(first and check).performScrollTo().performClick()
        val progress = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
        compose.onNode(first and progress).assertIsDisplayed()
        compose.runOnIdle { images.complete(listOf(item, second)) }
        compose.onAllNodes(first and hide).assertCountEquals(1)
        compose.onNode(first and hasText(label(R.string.photos_pending))).assertHasNoClickAction()
        compose
            .onNode(
                first and hasText(compose.activity.getString(R.string.moderation_photo_number, 2)),
            ).performScrollTo()
            .assertIsDisplayed()
        compose.onNode(first and hide).performScrollTo().performClick()
        compose.onNode(first and show).assertIsDisplayed()
        compose.onNode(first and hasText(label(R.string.photos_pending))).assertDoesNotExist()
        compose.onNode(other and check).performScrollTo().performClick()
        compose.onNode(other and hasText(label(R.string.photos_offline))).performScrollTo().assertIsDisplayed()
        compose.onNode(other and hasText(label(R.string.home_retry))).performScrollTo().performClick()
        compose.onNode(other and hasText(label(R.string.moderation_no_attachments))).performScrollTo().assertIsDisplayed()
        compose.onNode(other and show).assertDoesNotExist()
        compose.onNode(other and hide).assertDoesNotExist()
    }

    @Test
    fun photoQueueRefreshAndDecisionsUseTheExistingPhotoIds() {
        val first = PhotoSubmission(id, alice, target, PhotoStatus.PENDING, null)
        val second = first.copy(id = bob)
        val photos = FakePhotos(first)
        photos.readQueue = { listOf(first, second).filterNot { photo -> photos.decisions.any { it.second == photo.id } } }
        val model = PhotosViewModel(compose.activity.applicationContext, FakeAuth(profile(alice, UserRole.MODERATOR)), photos)
        compose.setContent {
            SportSpotTheme(darkTheme = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) { PhotoModeration(alice, model) }
            }
        }
        val approve = label(R.string.moderation_approve)
        val reject = label(R.string.moderation_reject)
        compose.onNodeWithText(label(R.string.photos_refresh)).performClick()
        compose.runOnIdle { assertEquals(listOf(null, null), photos.queueRequests) }
        compose.onAllNodes(hasText(approve))[0].performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(Triple(alice, id, null)), photos.decisions) }
        compose.onNodeWithText(reject).performScrollTo().performClick()
        compose.onNodeWithText(label(R.string.moderation_reason)).performTextInput("Фото не относится к площадке")
        compose.onNodeWithText(label(R.string.moderation_confirm_rejection)).performClick()
        compose.runOnIdle { assertEquals(Triple(alice, bob, "Фото не относится к площадке"), photos.decisions.last()) }
        compose.onNodeWithText(label(R.string.photos_queue_empty)).assertIsDisplayed()
    }

    @Test
    fun spotPhotoSectionKeepsOneRefreshAndSendsThePickedImage() {
        val context = compose.activity.applicationContext
        val source = File.createTempFile("photo-picker-test", ".jpg", context.cacheDir)
        try {
            source.writeBytes(image(128, 64))
            val photos = FakePhotos(null)
            val model = PhotosViewModel(context, FakeAuth(profile(alice)), photos)
            val narrow = mutableStateOf(false)
            val picker = TestPhotoPicker(Uri.fromFile(source))
            val spot = Spot(SpotId("osm", "way:7"), "Площадка у набережной", SportType.TENNIS, GeoPoint(56.3, 44.0))
            compose.setContent {
                SportSpotTheme(darkTheme = !narrow.value) {
                    val density = Density(LocalDensity.current.density, if (narrow.value) 1.6f else 1f)
                    CompositionLocalProvider(
                        LocalDensity provides density,
                        LocalActivityResultRegistryOwner provides picker,
                    ) {
                        Surface(
                            modifier = Modifier.size(if (narrow.value) 300.dp else 400.dp, 670.dp).testTag("spot-photo-details"),
                            color = MaterialTheme.colorScheme.background,
                        ) {
                            SpotDetailsScreen(
                                spotId = spot.id,
                                state = HomeUiState.Content(listOf(spot)),
                                onRetry = {},
                                onBack = {},
                                onOpenReviews = {},
                                onShowMap = {},
                                userPhotos = { SpotUserPhotos(alice, PhotoTarget.spot(it.id), model, onOpenAuth = {}) },
                            )
                        }
                    }
                }
            }
            val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            val choose = compose.onNodeWithText(label(R.string.photos_choose))
            val refresh = compose.onNodeWithText(label(R.string.photos_refresh))
            for (small in listOf(false, true)) {
                compose.runOnIdle { narrow.value = small }
                compose.onNode(hasText(label(R.string.photos_gallery)) and heading).performScrollTo().assertIsDisplayed()
                compose.onNodeWithText(label(R.string.photos_empty_gallery)).performScrollTo().assertIsDisplayed()
                compose.onNode(hasText(label(R.string.photos_add)) and heading).assertHasNoClickAction()
                compose.onAllNodesWithText(label(R.string.photos_refresh)).assertCountEquals(1)
                refresh.performScrollTo().assertHeightIsAtLeast(48.dp).assertIsEnabled()
                val mine = compose.onNodeWithText(label(R.string.photos_my_uploads)).fetchSemanticsNode().boundsInRoot
                val refreshTop = refresh.fetchSemanticsNode().boundsInRoot.top
                assertEquals(small, refreshTop >= mine.bottom)
                choose
                    .performScrollTo()
                    .assertHeightIsAtLeast(48.dp)
                    .assertWidthIsAtLeast(200.dp)
                    .assertIsEnabled()
                captureSpotPhotoDetails(if (small) "spot-photo-light-narrow.png" else "spot-photo-dark.png")
            }
            compose.runOnIdle {
                assertEquals(1, photos.historyReads)
                assertEquals(1, photos.galleryReads)
            }
            refresh.performScrollTo().performClick()
            compose.runOnIdle {
                assertEquals(2, photos.historyReads)
                assertEquals(2, photos.galleryReads)
            }
            choose.performScrollTo().performClick()
            val send = label(R.string.photos_send)
            compose.waitUntil(5_000) { compose.onAllNodesWithText(send).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText(send).performScrollTo().performClick()
            compose.onNodeWithText(label(R.string.photos_pending)).performScrollTo().assertIsDisplayed()
            compose.runOnIdle {
                assertEquals(1, picker.launches)
                assertEquals(alice, photos.uploads.single().ownerId)
                assertEquals(target, photos.uploads.single().target)
            }
        } finally {
            source.delete()
        }
    }

    @Test
    fun guestSeesTheUploadHeadingAndUsesTheExistingSignInAction() {
        val model = PhotosViewModel(compose.activity.applicationContext, FakeAuth(AuthState.Guest), FakePhotos(null))
        var signIns = 0
        compose.setContent {
            SportSpotTheme {
                SpotUserPhotos(null, target, model) { signIns++ }
            }
        }
        compose.onNodeWithText(label(R.string.photos_add)).assertHasNoClickAction()
        compose.onNodeWithText(label(R.string.photos_choose)).assertDoesNotExist()
        compose.onNodeWithText(label(R.string.photos_refresh_gallery)).assertIsEnabled()
        compose.onNodeWithText(label(R.string.content_open_auth)).performClick()
        compose.runOnIdle { assertEquals(1, signIns) }
    }

    @Test
    fun preparationResizesJpegStripsGpsAndRejectsNonImages() =
        runBlocking {
            val context = compose.activity.applicationContext
            val source = File.createTempFile("fixture-photo", ".jpg", context.cacheDir)
            try {
                source.writeBytes(image(2400, 1200))
                ExifInterface(source.path).apply {
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE, "56/1,1/1,0/1")
                    setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    saveAttributes()
                }
                assertTrue(ExifInterface(source.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null)
                val bytes = preparePhoto(context, Uri.fromFile(source)).jpeg
                assertTrue(bytes.size <= MAX_USER_PHOTO_BYTES)
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                assertEquals("image/jpeg", options.outMimeType)
                assertEquals(800, options.outWidth)
                assertEquals(1600, options.outHeight)
                source.writeBytes(bytes)
                assertNull(ExifInterface(source.path).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
                source.writeText("<svg />")
                val failure = runCatching { preparePhoto(context, Uri.fromFile(source)) }.exceptionOrNull() as? PhotoException
                assertEquals(PhotoError.INVALID, failure?.error)
            } finally {
                source.delete()
            }
            Unit
        }

    private fun label(res: Int) = compose.activity.getString(res)

    private fun captureSpotPhotoDetails(fileName: String) {
        if (InstrumentationRegistry.getArguments().getString("spotPhotoScreenshots") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onNodeWithTag("spot-photo-details").captureToImage().asAndroidBitmap()
        context.filesDir
            .resolve(fileName)
            .outputStream()
            .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private class TestPhotoPicker(
        private val chosen: Uri,
    ) : ActivityResultRegistryOwner {
        var launches = 0
            private set

        override val activityResultRegistry =
            object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?,
                ) {
                    assertTrue(contract is ActivityResultContracts.PickMultipleVisualMedia)
                    launches++
                    dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(chosen))
                }
            }
    }

    private fun profile(
        id: String,
        role: UserRole = UserRole.USER,
    ) = AuthState.Authenticated(UserProfile(id, "Tester", "tester@example.invalid", role))

    private class FakeAuth(
        initialState: AuthState,
    ) : AuthRepository {
        override val authState = MutableStateFlow(initialState)

        override suspend fun getAuthState() = authState.value

        override suspend fun signIn(
            email: String,
            password: String,
        ): UserProfile = error("Unexpected login")

        override suspend fun signUp(
            name: String,
            email: String,
            password: String,
        ) = Unit

        override suspend fun signOut() {
            authState.value = AuthState.Guest
        }
    }

    private class FakePhotos(
        private val item: PhotoSubmission?,
    ) : UserPhotoRepository {
        var approved = false
        var lastPreviewOwner: String? = "No image requested"
        var readQueue: suspend (String?) -> List<PhotoSubmission> = { emptyList() }
        val queueRequests = mutableListOf<String?>()
        val decisions = mutableListOf<Triple<String, String, String?>>()
        val uploads = mutableListOf<PhotoSubmission>()
        var galleryReads = 0
        var historyReads = 0

        override suspend fun upload(
            ownerId: String,
            target: PhotoTarget,
            attachment: PhotoAttachment,
        ): PhotoSubmission = PhotoSubmission(attachment.id, ownerId, target, PhotoStatus.PENDING, null).also { uploads.add(it) }

        override suspend fun mine(
            ownerId: String,
            target: PhotoTarget,
        ): List<PhotoSubmission> {
            historyReads++
            return (listOfNotNull(item) + uploads).filter { it.ownerId == ownerId && it.target == target }
        }

        override suspend fun remove(
            ownerId: String,
            photoId: String,
        ) = Unit

        override suspend fun gallery(target: PhotoTarget): List<String> {
            galleryReads++
            return if (approved && item?.target == target) listOf(item.id) else emptyList()
        }

        override suspend fun image(
            photoId: String,
            previewOwnerId: String?,
        ): ByteArray {
            lastPreviewOwner = previewOwnerId
            return image(128, 64)
        }

        override suspend fun queue(
            moderatorId: String,
            proposalId: String?,
        ): List<PhotoSubmission> {
            queueRequests.add(proposalId)
            return readQueue(proposalId)
        }

        override suspend fun decide(
            moderatorId: String,
            photoId: String,
            rejectionReason: String?,
        ) {
            decisions.add(Triple(moderatorId, photoId, rejectionReason))
        }
    }
}

private fun image(
    width: Int,
    height: Int,
): ByteArray {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        bitmap.eraseColor(Color.GREEN)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            output.toByteArray()
        }
    } finally {
        bitmap.recycle()
    }
}
