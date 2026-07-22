# Скрипт автопублікації

Автоматизація публікації iOS-додатків у App Store для KMP-проєктів.
Скрипт читає метадані додатку з Jira-тікета, редагує файли проєкту, пушить у
продакшн-акаунт GitHub, запускає білди в Codemagic, заповнює Google-таблицю метаданих
та вивантажує все до App Store Connect.

Точка входу: `python -m scripts.publish` з кореня репозиторію.

Скрипт працює у **двох режимах**:

- **Локально** — інтерактивно, конфіг із `../Utils/local.properties`, з підтвердженнями
  перед кожною деструктивною дією.
- **У CI** — job `publish_ios` (android-ci-policy, раннер `ios-builder`) запускає
  `appData/publish_ios.sh`, який виконує `python -m scripts.publish --yes` неінтерактивно;
  увесь конфіг береться з env-змінних.

---

## Налаштування

Виконується один раз на машину. Все, що потрібно робити перед кожним запуском,
описано в наступному розділі.

### 1. Python + залежності

- Python 3.10 або новіший.
- З кореня репозиторію:

  ```
  pip install -r scripts/requirements.txt
  ```

  Це підтягне `requests`, `gspread`, `google-auth`, `pillow`,
  `claude-agent-sdk` та `anyio`. Скрипт-оркестратор **не звертається до Apple
  напряму** — усі виклики App Store Connect виконує fastlane/spaceship на
  Codemagic (кроки 14-15), тож окремих залежностей для цього не треба.

- `claude-agent-sdk` потребує встановленого Claude Code у `PATH` з активною
  авторизацією (`claude login`). Генератор метаданих використовує модель
  `claude-sonnet-4-6`, щоб створити subtitle/description/keywords і скоротити
  будь-який текст, що перевищує ліміти символів App Store.

### 2. Конфіг: env-змінні або `../Utils/local.properties`

`scripts/publish/config.py` бере кожен ключ **спочатку з env-змінної**, і лише якщо її нема —
з `../Utils/local.properties`. Тобто локально достатньо файла, а в CI усе передається через env.

```
<батьківська тека>/
  1234-Super-Prila/    ← папка вашої пріли
  Utils/
    local.properties   ← тут лежать секрети
```

Обов'язкові ключі:

| Ключ (`local.properties`)       | Env-змінна (пріоритет)                  | Що це                                                          |
|---------------------------------|-----------------------------------------|----------------------------------------------------------------|
| `jira.baseUrl`                  | `JIRA_BASE_URL`                         | Базовий URL Jira REST (напр. `https://itrident.atlassian.net`) |
| `jira.browseBaseUrl`            | `JIRA_BROWSE_BASE_URL`                  | URL для посилань на тікети; якщо не задано — дорівнює base URL |
| `jira.email`                    | `JIRA_EMAIL`                            | Email акаунта Atlassian                                        |
| `jira.apiToken`                 | `JIRA_API_TOKEN`                        | API-токен Atlassian (Read)                                     |
| `google.sheets.credentialsPath` | `GOOGLE_SHEETS_CREDENTIALS_PATH`        | Шлях до JSON-ключа service account                             |
| —                               | `GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64` | **base64** того самого JSON — декодується у тимчасовий файл    |

Відсутні ключі одразу спричинять зупинку роботи скрипта зі списком того, що треба
додати.

Приклад файлу `local.properties`:

```
jira.baseUrl=https://api.atlassian.com/ex/jira/c6470e67-3353-4ac4-8fe0-5c7886ca6890
jira.browseBaseUrl=https://itrident.atlassian.net
jira.email=oleksandr.volovyk@itrident.agency
jira.apiToken=ATATT...
google.sheets.credentialsPath=D:\work\Utils\appstorepublisher-cb875d669d62.json
```

### 3. Service account для Google Sheets

- Створіть у Google Cloud service account з увімкненим API Google Sheets.
- Завантажте його JSON-ключ і вкажіть шлях у `google.sheets.credentialsPath` (наприклад, покладіть
  його в `Utils`, поряд з `local.properties`).
- CI той самий JSON кладе у змінну `GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64` (`base64` від вмісту файла).

---

## Порядок дій на кожному запуску

### Перед запуском

**Ім'я теки проєкту має починатися з номера тікета.**
Скрипт виводить номер тікета з початкових цифр у назві теки
(напр. `1234-something/` → тікет `1234`). Якщо цифр немає — запитає вручну.

> У CI цей крок не потрібен: якщо задано `JIRA_ISSUE_KEY`, тікет тягнеться **напряму за ключем**, 
> а номер із назви теки/репо використовується лише як код пріли для Codemagic-групи та 
> вкладки Google Sheet.

### Запуск

```
python -m scripts.publish
```

Скрипт проходить пронумеровані кроки. Перед кожною деструктивною дією
(створення гілки, пуш, запуск білдів у Codemagic) запитує підтвердження.

### Що саме відбувається, покроково

| Крок   | Що виконується                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
|--------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0      | Завантажуємо конфіг. Виводимо номер тікета з назви теки. Тягнемо Jira-тікет і пов'язану IF-сторі. Виводимо summary-блок.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 0.5    | **Валідація даних акаунта** (`validation.py`) — перевіряємо всі заповнені руками поля тікета + IF-сторі на наявність і формат (bundle, Team/Key ID, Issuer UUID, Apple ID, privacy URL, email, телефони в E.164, пароль, 2FA number/link тощо) і виводимо **єдиний звіт з усіма проблемами одразу**. Будь-яка помилка → зупинка тут, ще до правок файлів / пушу / білдів Codemagic (інакше це впало б пізно, напр. на Upload Metadata). Попередження (напр. ім'я без прізвища) не блокують. У CI (`--yes`) додатково вимагаємо `Codemagic API Token` / `GitHub Token` на тікеті. Зовнішні сервіси (`.p8`, Codemagic, GitHub, Sheets, Claude) окремо перевіряє preflight.                                                                                                                                                                                             |
| 1-4, 6 | Редагуємо файли проєкту: `Config.xcconfig` (TEAM_ID, PRODUCT_BUNDLE_IDENTIFIER), `iosApp.xcodeproj/project.pbxproj` (те саме), `Info.plist` (додаємо `ITSAppUsesNonExemptEncryption=false`, якщо ключа ще немає — без нього Apple вимагає export-compliance інтерактивно й сабміт неможливий), `codemagic.yaml` (env-група `"9999"` → номер тікета), `iosApp/fastlane/Fastfile` (блок `app_review_information`), `gradle.properties` (секція `#Gradle`).                                                                                                                                                                                                                                                                                                                                                                                                             |
| 5      | Перевіряємо `iosApp/fastlane/white/` на наявність скріншотів. Якщо порожньо — автоматично тягнемо архів із Jira (див. «Скріншоти» нижче). Якщо й там нема: локально чекаємо ручного завантаження, у CI (`--yes`) — помилка.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 7      | Запускаємо `iosApp/fastlane/populate_locales_white.py` — копіює скріншоти в кожну локаль (з ресайзом, якщо треба).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| —      | Шукаємо додаток у Codemagic (спочатку за ім'ям = номер тікета, потім номер-1). Виводимо GitHub-репозиторій з прив'язаного до Codemagic-додатку репо, щоб не набирати вручну.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| 8-9    | Створюємо гілку `ios_release`, додаємо `production` remote (`https://<user>:<token>@github.com/...`), комітимо все, пушимо.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| 10     | Оновлюємо змінні середовища в Codemagic у групі з іменем = номер тікета: `APP_STORE_CONNECT_PRIVATE_KEY` (вміст `.p8`, секрет), `DEVELOPMENT_TEAM`, `GSHEET_TAB`, `GSHEET_ID`, `CM_APP_STORE_APPLE_ID`, `APP_STORE_CONNECT_KEY_IDENTIFIER`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_IDENTIFIER`, `APPLE_ID`, і — якщо в тікеті є `Klo link` — `ASC_WEBHOOK_URL` + `ASC_WEBHOOK_SECRET` (секрет) для webhook статусів.                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| 11     | Запускаємо воркфлоу `ios_kmp_release` у Codemagic на гілці `ios_release`: білдить IPA, підписує, **заливає білд у App Store Connect** (upload-only через `app-store-connect publish`, **БЕЗ** TestFlight beta review — для App Store-релізу він не потрібен, а вимагав би незаповнених Beta App Review / Feedback Email). **Білд робимо ЗАВЖДИ** — цей самий воркфлоу перезапускають і для оновлень версій, тож ребілд пропускати не можна. Дедуплікується лише **аплоад**, за ТОЧНИМИ version+build самого IPA: повторний запуск тієї самої публікації → та сама версія вже на ASC → аплоад пропускаємо (щоб не падати на «версія вже існує»); перша публікація або оновлення → нова версія/білд → заливаємо. Сабміт потім прив'яже потрібний білд.                                                                                                                                                                                                                                                                                       |
| 12     | Генеруємо текст для App Store через Claude Agent SDK (subtitle, description, keywords) і одночасно визначаємо категорії App Store (primary/secondary) одним запитом. Дублюємо вкладку `Template 3` у спільній таблиці в нову вкладку з іменем = номер тікета. Створюємо Telegraph-сторінку підтримки (закешований токен акаунта перевикористовується між запусками). Пишемо рядок `en-GB` у таблицю (єдина локаль).                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| 12.5   | Перевіряємо метадані `en-GB` відповідно до лімітів App Store (name 30, subtitle 30, keywords 100, description 4000). Якщо поле перевищує ліміт — скорочуємо текст через Claude Agent SDK.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 13     | Запускаємо воркфлоу `upload_ios_metadata` у Codemagic. Він витягує таблицю, записує файли `fastlane/metadata/` і заливає їх разом зі скріншотами в App Store Connect.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| 14     | Усередині воркфлоу `upload_ios_metadata` (після `deliver`) запускається fastlane-лейн **`set_app_compliance`** (spaceship, .p8): primary locale = `en-GB` (щоб збігалася з єдиною локаллю метаданих) + видалення решти локалей (залишкова локаль без метаданих блокує сабміт), Content Rights = no third-party content, ціна = Free, доступність = усі території + авто-додавання нових, і реєстрація **webhook** статусів App Store Connect (якщо в тікеті є поле `Klo link` → крок 10 кладе `ASC_WEBHOOK_URL`). Далі лейн **`web_session_declarations`** (веб-сесія, не .p8) робить App Privacy = «Data Not Collected» (і одразу **публікується** — без цього Apple не дає сабмітити), DSA «not a trader», і форму regulated-medical-device = «No» (коли Apple показує це питання для застосунку; інакше POST віддає 404 і крок тихо пропускається). Деталі нижче. |
| 14.5   | **Гейт черги Codemagic.** Оркестратор ЧЕКАЄ (опитує `GET /builds/{id}`), поки білди `ios_kmp_release` і `upload_ios_metadata` завершаться, і лише тоді тригерить submit. На Free-плані білди йдуть по одному, а черга Codemagic може переплутати порядок близьких за часом тригерів — тому не покладаємось на порядок тригерів, а явно чекаємо на завершення. Якщо якийсь із цих білдів упав — submit НЕ запускається. Таймаути в `constants.py` (`*_BUILD_TIMEOUT`). |
| 15     | Воркфлоу `submit_ios_for_review` запускає fastlane-лейн **`submit_for_review`** (spaceship): чекає, поки білд пройде обробку Apple (10–60 хв), прив'язує білд до версії та відправляє на рев'ю (3-кроковий reviewSubmissions). Авторелiз після апруву вже ввімкнено в `deliver` (`automatic_release: true`). Оркестратор чекає й на цей білд — підсумковий exit-код скрипта відображає реальний результат усіх трьох білдів.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |

Наприкінці скрипт друкує summary-табличку з клікабельними URL-ами на
Jira-тікет, IF-сторі, GitHub-репозиторій, Codemagic-додаток і три білди
(release / metadata / submit), вкладку Google Sheet та Telegraph-сторінку
підтримки. Також виконує `git credential-manager github logout <user>`, щоб
продакшн-акаунт не лишався залогіненим. Якщо хоч один Codemagic-білд не завершився
успішно, скрипт після summary падає з ненульовим кодом — тому CI (android-ci-policy)
більше не позначає неуспішну публікацію як успішну лише через те, що скрипт «доїхав» до кінця.

### Архітектура: усе через fastlane/spaceship на Codemagic

Оркестратор (`python -m scripts.publish`) **ніколи не звертається до Apple
напряму** — він лише редагує файли, пушить у GitHub і тригерить Codemagic-білди.
Усі виклики App Store Connect (декларації + сабміт) виконує **fastlane/spaceship**
у воркфлоу на Codemagic. Так увесь трафік до Apple йде з одного CI-IP і має
очікувану fastlane-форму запитів — це важливо, бо ми масово публікуємо застосунки
й не хочемо, щоб Apple заблокував кастомний клієнт чи IP.

Лейни в `iosApp/fastlane/Fastfile`:
- `set_app_compliance` — primary locale (`patch_app primaryLocale: "en-GB"`,
  ідемпотентно; має йти після `deliver`, який створює локалізацію en-GB, тому
  en-GB стає валідною primary) + видалення решти локалей (`prune_locales_to_en_gb!`:
  саме зміни primary недостатньо — залишкова локаль на кшталт en-US без метаданих
  блокує сабміт, тож видаляємо всі не-en-GB локалізації і версії застосунку, і
  App Info; поточну primary видалити не можна, тому крок іде ПІСЛЯ зміни primary)
  + content rights (`patch_app`, лише attributes) + Free
  (через `appPriceSchedules` + $0 price point: Apple прибрав легасі price-tier
  `prices`-relationship на `apps`, тож `patch_app(app_price_tier_id:)` більше не
  працює — шукаємо безкоштовний price point для USA і POST-имо manual-price
  schedule); доступність на всі території + нові (raw `v2/appAvailabilities` через
  `tunes_request_client`; territoryAvailabilities створюються inline, тож id —
  JSON:API local-placeholder `${USA}` тощо, а не код території; v1 більше нема).
  Плюс реєстрація webhook (`Spaceship::ConnectAPI::Webhook.create`, per-app, .p8): підписка на всі
  типи подій Apple (мінімум `APP_STORE_VERSION_APP_VERSION_STATE_UPDATED` — стан
  рев'ю), з фолбеком на лише цю подію, якщо Apple відхилить повний набір.
  Ідемпотентно (за іменем `auto-publish-status`); URL/secret беруться з
  `ASC_WEBHOOK_URL`/`ASC_WEBHOOK_SECRET`; якщо `ASC_WEBHOOK_URL` порожній — крок
  пропускається. Помилка webhook **не** валить сабміт.

  URL збирається оркестратором із поля `Klo link` тікета (голий домен, напр.
  `domain.com`) як `https://api.<domain>/<rand>` (бекенд валідує за форматом
  payload, тому випадковий суфікс і secret — будь-які). Поле Jira називається
  `Klo link` (див. `OPTIONAL_APP_TICKET_FIELDS`).
- `web_session_declarations` — App Privacy + DSA + regulated-medical-device
  (усі без .p8 API) за ОДИН веб-логін (авто SMS-2FA): App Privacy «Data Not
  Collected» + публікація (`AppDataUsage` + `AppDataUsagesPublishState#publish!`
  через iris), тоді DSA «not a trader» через сервіс `ppm`, і форма
  regulated-medical-device = «No» через сервіс `ppm/complianceform` (див. нижче).
  Усі ідемпотентні; App Privacy фатальний (блокує сабміт), DSA і medical-device —
  non-fatal.
- `submit_for_review` — очікування обробки білда (`Build.all` зі станом `VALID`),
  прив'язка білда до версії (`patch_app_store_version_with_build`), 3-кроковий
  `ReviewSubmission`.

### DSA trader status — автоматизовано через `ppm` (веб-сесія, НЕ .p8)

DSA-декларація йде через сервіс **`ppm`** (agreements) на
`appstoreconnect.apple.com`, автентифікований **веб-сесією** (cookies + заголовок
`X-CSRF-ITC: agreements-ui`), а **не** ключем `.p8`. spaceship не має ppm-клієнта,
тож `declare_not_trader!` робить raw-запити з cookie сесії
(`Spaceship::Tunes.client.cookie`). Account-level + ідемпотентно (пропускає, якщо
вже `DECLARED_NOT_TRADER`). Повний потік (з HAR):

```
GET  /ppm/v1/accountLookup/teamTypes/ITC/teamIds/{teamId}          → accountId (UUID)
GET  /ppm/v1/accounts/{accountId}/sellerInfo                       → traderDeclaration (ідемпотентність)
GET  /ppm/v1/accounts/{accountId}/legalEntities                    → data[0].legalEntityId
POST /ppm/v1/accounts/{accountId}/legalEntities/{leId}/sellerInfo/metadata
     {"accountId","legalEntityId"}                                 → data.optimisticLock
POST /ppm/v1/accounts/{accountId}/legalEntities/{leId}/sellerInfo
     {"accountId","legalEntityId","optimisticLock","traderDeclaration":"DECLARED_NOT_TRADER"}
```
`teamId` = `Spaceship::Tunes.client.team_id`; accountLookup мапить його в `accountId`.

**Regulated medical device — автоматизовано (веб-сесія, НЕ .p8).** Лейн
`web_session_declarations` викликає `declare_not_medical_device!`, який POST-ить
форму = «No» **напряму** — робочого GET-списку вимог немає (колекційний GET віддає
404). `requirementName` `MEDICAL_DEVICE` — глобальна вимога, тож `requirementId`
стабільний між застосунками (per-app лише сама форма/`formId`); значення взяте з
HAR і перевизначається env-змінною `MEDICAL_DEVICE_REQUIREMENT_ID`, якщо Apple
колись його змінить. На відміну від DSA (`agreements-ui`), complianceform вимагає
заголовок `X-CSRF-ITC: [asc-ui]` і Referer сторінки `distribution/info` (звідси
окремі параметри `csrf`/`referer`/`accept` у `ppm_http`). Питання інколи
показується і для не-медичних застосунків — тоді ми теж сабмітимо «No»; якщо ж
воно взагалі не застосовне, POST повертає 404 і крок тихо пропускається. Запит:

```
POST /ppm/complianceform/v1/accounts/{accountId}/contents/{appId}/requirements/{requirementId}/forms
     {"accountId","contentId":{appId},"requirementId","requirementName":"MEDICAL_DEVICE",
      "countriesOrRegions":["EEA","GBR","USA"],"medicalDeviceData":{"declaration":"no"}}
```
`{appId}` = `CM_APP_STORE_APPLE_ID`; `{accountId}` резолвиться так само, як у DSA
(`ppm_account_id`). Усі виклики потребують активної веб-сесії (`X-CSRF-ITC` +
cookies). Ідемпотентно (повторний POST «no» повертає 200), non-fatal.

### Скріншоти (авто-завантаження з Jira)

Скріншоти беруться з теки `iosApp/fastlane/white/`. Якщо вона порожня, крок 5 пробує
підтягнути їх із Jira автоматично — по черзі, перший успішний виграє:

1. **Design-zip** — у parent-тікета шукається пов'язаний тікет проєкту `DES-*` із summary
   «IOS White Design»; береться його перший `.zip`.
2. **Скріншот-zip** — перший `.zip`, чиє ім'я містить водночас `ios` і `screen`, прикріплений
   до самого тікета або його parent-а.

Архів розпаковується у `white/` з фільтрами: пропускаються `__MACOSX/` й AppleDouble-файли
(`._*`), файли з `icon` у назві та все, що не має PNG/JPEG-сигнатури. Дублі імен розводяться суфіксом
`_1`, `_2`, …

Якщо після авто-фетчу зображень так і немає:

- **локально** — скрипт чекає, поки ви вручну закинете зображення у `white/` і натиснете Enter;
- **у CI (`--yes`)** — падає з помилкою.

App Store приймає щонайбільше **10** зображень — зайві (понад перші 10) видаляються 
(з логом-попередженням).

---

## Запуск у CI

У CI скрипт виконує job `publish_ios` (android-ci-policy) на macOS-раннері `ios-builder`:
`appData/publish_ios.sh` → `python -m scripts.publish --yes` (без підтверджень).

Конфіг — з env-змінних (таблиця вище): `JIRA_*` + `GOOGLE_SHEETS_CREDENTIALS_JSON_BASE64`. Окремо:

- `JIRA_ISSUE_KEY` — iOS-тікет, з якого спрацювала Jira Automation; за ним тягнеться тікет напряму.
- **Codemagic API token і GitHub PAT** беруться з полів Jira-тікета
  («Codemagic API Token» / «GitHub Token»); локально, якщо поле порожнє, скрипт запитає їх.

**Повторний запуск ідемпотентний** (тікет міг публікуватися раніше):

- Якщо `production/ios_release` вже існує — Release ребейзиться поверх нього (`git reset --soft`).
- Codemagic-змінні перезаписуються.

## Відомі підводні камені

- **Ім'я теки проєкту має починатися з номера тікета.**
  Скрипт виводить номер тікета з початкових цифр у назві теки
  (напр. `1234-something/` → тікет `1234`). Якщо цифр немає — запитає.

- **Сусідня папка `Utils/` потрібна лише локально.** Якщо конфіг задано env-змінними
  (як у CI), `../Utils/local.properties` не потрібен зовсім. Локально ж іншим командам,
  що беруть цей шаблон, потрібен власний `../Utils/local.properties` (або пропатчити
  `UTILS_LOCAL_PROPS` у `scripts/publish/config.py`, щоб він дивився в інше місце).


