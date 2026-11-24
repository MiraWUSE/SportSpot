package com.mirawuse.sportspot.presentation.ui.photos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.mirawuse.sportspot.domain.model.auth.AuthState
import com.mirawuse.sportspot.domain.model.photo.MAX_USER_PHOTO_BYTES
import com.mirawuse.sportspot.domain.model.photo.PhotoAttachment
import com.mirawuse.sportspot.domain.model.photo.PhotoError
import com.mirawuse.sportspot.domain.model.photo.PhotoException
import com.mirawuse.sportspot.domain.model.photo.PhotoStatus
import com.mirawuse.sportspot.domain.model.photo.PhotoSubmission
import com.mirawuse.sportspot.domain.model.photo.PhotoTarget
import com.mirawuse.sportspot.domain.model.photo.PhotoTargetType
import com.mirawuse.sportspot.domain.model.user.UserProfile
import com.mirawuse.sportspot.domain.model.user.UserRole
import com.mirawuse.sportspot.domain.repository.AuthRepository
import com.mirawuse.sportspot.domain.repository.UserPhotoRepository
import com.mirawuse.sportspot.presentation.R
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
        private val item: PhotoSubmission,
    ) : UserPhotoRepository {
        var approved = false
        var lastPreviewOwner: String? = "No image requested"
        var readQueue: suspend (String?) -> List<PhotoSubmission> = { emptyList() }
        val queueRequests = mutableListOf<String?>()
        val decisions = mutableListOf<Triple<String, String, String?>>()

        override suspend fun upload(
            ownerId: String,
            target: PhotoTarget,
            attachment: PhotoAttachment,
        ): PhotoSubmission = error("Unexpected upload")

        override suspend fun mine(
            ownerId: String,
            target: PhotoTarget,
        ) = listOf(item).filter { it.ownerId == ownerId && it.target == target }

        override suspend fun remove(
            ownerId: String,
            photoId: String,
        ) = Unit

        override suspend fun gallery(target: PhotoTarget) = if (approved && item.target == target) listOf(item.id) else emptyList()

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
