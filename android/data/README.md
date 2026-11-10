# Сетевой слой SportSpot — практика №15

Модуль `data` реализует цепочку HTTP → Retrofit → DTO → mapper → Domain.
Существующие модели, интерфейс `SpotRepository` и Use Cases модуля `domain` не изменены.
UI, Room, хранение сессии, refresh token и операции записи в backend в эту практику не входят.

## Структура

Пути ниже указаны относительно `android/data/`.

| Путь в `src/main/java/com/mirawuse/sportspot/data/` | Назначение |
| --- | --- |
| `remote/NetworkConfig.kt`, `remote/NetworkClient.kt` | Адрес, таймауты, общий OkHttp/Retrofit и строгий Gson |
| `remote/*Interceptor.kt`, `remote/AccessTokenProvider.kt` | Заголовки и безопасные логи |
| `remote/api/SpotApi.kt`, `remote/dto/` | Два GET endpoint и wire-модели OpenAPI |
| `remote/error/` | `NetworkError`, `NetworkException` и единая граница `NetworkCall` |
| `remote/connectivity/` | Проверка состояния сети Android и заменяемый `NetworkMonitor` |
| `mapper/` | DTO → Domain, преобразование спорта и canonical ID |
| `repository/SpotRepositoryImpl.kt` | Реализация трёх методов `SpotRepository` |
| `di/` | Singleton-провайдеры и Hilt bindings |

Тесты расположены в `src/test/java/.../data/`, JSON fixtures — в `src/test/resources/fixtures/`.

## Контракт и источники данных

Production base URL: `https://tnqkaamvvxwsimfydurg.supabase.co/`.
Первичные источники контракта: корневой `openapi.yaml`, серверный `search-logic.ts` и `backend/setup.sql`.

| Метод репозитория | HTTP API | Поведение |
| --- | --- | --- |
| `searchNearby` | `GET /functions/v1/search-sports-spots-v1` | Внешние OSM-объекты по `lat`, `lon`, `radius`, `sport` |
| `getSpots` | `GET /rest/v1/spots` | Сохранённые строки PostgreSQL, `select=*` |
| `getSpotById` | `GET /rest/v1/spots?id=eq.<canonical_id>` | Только сохранённая строка; массив ответа проверяется клиентом |

Публичный поиск не требует пользовательского JWT. Чтение REST использует `apikey` и серверные RLS.
Найденный внешний объект может отсутствовать в таблице `spots`; отдельного OSM lookup endpoint нет.
`getSpotById`: HTTP 200 с `[]` → `null`; HTTP 404 остаётся ошибкой.
Успешные пустые списки поиска и REST → `emptyList()`. Ошибки не маскируются пустыми результатами.

`getSpots` читает страницы по 100 строк с `order=id.asc` и `offset` до короткой страницы.
Дубликаты ID и повтор страницы → `Parsing`; ошибка следующей страницы не возвращает частичный список.
Это offset-пагинация без snapshot: параллельные изменения таблицы могут менять состав страниц.
Поиск возвращает максимум 100 объектов без пагинации; его metadata и `count` проверяются.
Особенность существующего backend: `sport=all` ищет `leisure=pitch`; `fitness_station` выбирается фильтром `fitness`.

## Зависимости и настройка ключа

В `android/gradle/libs.versions.toml` закреплены Retrofit 3.0.0, OkHttp/MockWebServer 4.12.0 и Gson 2.13.1.
Эта связка соответствует [каталогу Retrofit 3.0.0](https://github.com/square/retrofit/blob/3.0.0/gradle/libs.versions.toml).
Она проверяется сборкой текущего Android-проекта; динамические версии не используются.

Publishable key задаётся одним из способов:

1. Переменная окружения сборки `SUPABASE_PUBLISHABLE_KEY` — имеет приоритет.
2. Свойство `supabase.publishableKey` в игнорируемом Git файле `android/local.properties`.

Gradle допускает пустое значение либо формат `sb_publishable_` с последующими буквами, цифрами, `_` или `-`.
Пустая переменная окружения также имеет приоритет над локальным свойством.
Без ключа доступны сборка, автономные тесты и клиент публичного поиска; REST требует настроенного ключа.
Legacy `anon`, `service_role` и `sb_secret_` в конфигурации Android не принимаются.
Реальные значения ключей и пользовательские JWT не следует добавлять в исходники, fixtures или логи.
Publishable key попадает в `BuildConfig` и APK; права доступа к данным определяет серверная RLS.

`AccessTokenProvider` в Hilt намеренно возвращает `null`: полноценная авторизация находится вне P15.
Будущая реализация provider должна выдавать пользовательский access JWT; клиент помещает его только в `Authorization`.
При отсутствии токена заголовок `Authorization` отсутствует. Publishable key передаётся отдельно в `apikey`.
Слой не сохраняет JWT, не обновляет токены и не назначает роли пользователя.

## HTTP и логирование

Production использует HTTPS; HTTP разрешён конфигурацией только для loopback-тестов.
Клиент допускает запросы только к настроенному origin; перенаправления и автоматические повторы отключены.

| Таймаут | Значение |
| --- | --- |
| Connect | 10 секунд |
| Read | 45 секунд |
| Write | 15 секунд |
| Call | 60 секунд |

DEBUG-лог содержит только метод, путь из whitelist, HTTP-статус и длительность.
Whitelist включает два поддерживаемых endpoint; другие пути заменяются на `/[redacted]`.
Query, координаты, заголовки, Cookie, тела и детали исключений не выводятся. В release логирование выключено.

## Маппинг и ошибки

Gson настроен на строгий JSON и отключённый `Unsafe`; nullable DTO явно проверяются перед созданием Domain.
Отсутствующие координаты не превращаются в нули. Невалидные поля, null-элементы и JSON → `Parsing`.
`lighting=null` остаётся `null`, а `false` сохраняется. Строки вместо JSON boolean отклоняются строгим адаптером.
`surface=null` также сохраняется.
`osm:node:12345` → `SpotId("osm", "node:12345")`; типы `way` и `relation` сохраняются аналогично.
`community:UUID` проверяется как canonical UUID; ID, `source`, `osm_type` и `osm_id` должны согласовываться.
Обратное преобразование ID не угадывает тип OSM; URL-кодирование выполняет Retrofit.

`soccer` → `FOOTBALL`, `fitness` → `WORKOUT`; остальные поддерживаемые виды спорта сопоставляются явно.
Неизвестный серверный вид спорта → `OTHER`. Исходящий фильтр `OTHER` → `InvalidRequest`; `null` → `all`.
Радиус должен находиться в `100..3000`. Координаты передаются независимо от Locale, без scientific notation.
Более шести десятичных знаков после удаления завершающих нулей → `InvalidRequest`: неявного округления нет.

Репозиторий выбрасывает `NetworkException` с типизированным `NetworkError`:

- `Http(status, serverCode, retryAfterSeconds)` сохраняет HTTP-код, включая 400/401/403/404/409/429/5xx.
- `Timeout`, `Offline`, `Dns`, `Connection` различают транспортные причины.
- `Parsing`, `InvalidRequest`, `Unexpected` различают некорректный ответ, параметры и прочие сбои.

Парсер поддерживает вложенную Edge Function ошибку и плоский REST/gateway JSON; текст/HTML/пустое тело безопасно игнорируются.
Сохраняется только ограниченный безопасный `serverCode`, без сырого сообщения сервера.
`Retry-After` поддерживается как неотрицательное число секунд; HTTP-date не разбирается.
Причина технического исключения сохраняется для диагностики и не должна записываться в сетевые логи.
`CancellationException` пробрасывается; отмена также проверяется перед классификацией IO.
`NetworkMonitor` проверяет `INTERNET` и `VALIDATED`; это сигнал о подключении, не гарантия доступности сервера.

## Проверка

Из папки `android` в PowerShell с доступным JDK выполните:

```powershell
.\gradlew.bat :domain:test
.\gradlew.bat :data:testDebugUnitTest
.\gradlew.bat ktlintCheck
.\gradlew.bat :app:assembleDebug
```

Android CI дополнительно запускает `:domain:test :data:testDebugUnitTest`; ключи и живой Supabase для них не нужны.
MockWebServer проверяет HTTP → DTO → mapper → Domain, запросы, ошибки, таймауты, отмену и безопасность логов.
`search-spots.json` повторяет пример поиска OpenAPI; `saved-spots.json` построен по полной схеме/SQL.
Варианты для негативных тестов и пагинации явно производны от контрактных fixtures, а не от живых записей.
Сокращённый REST-пример OpenAPI не содержит координат и подходит только как неполный ответ для отрицательной проверки.

Живые HTTP-запросы и изменения рабочей Supabase БД при реализации не выполнялись.
Android connectivity на устройстве/эмуляторе вручную не подтверждался; сборка и JVM-тесты не заменяют такую проверку.
