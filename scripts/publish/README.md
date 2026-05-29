# Скрипт автопублікації

Автоматизація публікації iOS-додатків у App Store для KMP-проєктів.
Скрипт читає метадані додатку з Jira-тікета, редагує файли проєкту, пушить у
продакшн-акаунт GitHub, запускає білди в Codemagic, заповнює Google-таблицю метаданих
та вивантажує все до App Store Connect.

Точка входу: `python -m scripts.publish` з кореня репозиторію.

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
  `claude-agent-sdk` та `anyio`.

- `claude-agent-sdk` потребує встановленого Claude Code у `PATH` з активною
  авторизацією (`claude login`). Генератор метаданих використовує модель
  `claude-sonnet-4-6`, щоб створити subtitle/description/keywords і скоротити
  будь-який текст, що перевищує ліміти символів App Store.

### 2. `../Utils/local.properties`

`scripts/publish/config.py` читає секрети з `../Utils/local.properties`

```
<батьківська тека>/
  1234-Super-Prila/    ← папка вашої пріли
  Utils/
    local.properties   ← тут лежать секрети
```

Обов'язкові ключі:

| Ключ                            | Що це                                                                                           |
|---------------------------------|-------------------------------------------------------------------------------------------------|
| `jira.baseUrl`                  | Базовий URL Jira REST, `https://api.atlassian.com/ex/jira/c6470e67-3353-4ac4-8fe0-5c7886ca6890` |
| `jira.browseBaseUrl`            | Базовий URL для посилань на тікети, `https://itrident.atlassian.net`                            |
| `jira.email`                    | Email вашого акаунта Atlassian                                                                  |
| `jira.apiToken`                 | API-токен Atlassian (id.atlassian.com → Security) з правами на Read всього                      |
| `google.sheets.credentialsPath` | Абсолютний шлях до файлу JSON-ключа Google service account                                      |

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

- Створіть у Google Cloud service account з Role "Owner", та з увімкненим API Google Sheets.
- Завантажте його JSON-ключ і вкажіть шлях у `google.sheets.credentialsPath` (наприклад, покладіть
  його в `Utils`, поряд з `local.properties`).

---

## Порядок дій на кожному запуску

### Перед запуском

**Ім'я теки проєкту має починатися з номера тікета.**
Скрипт виводить номер тікета з початкових цифр у назві теки
(напр. `1234-something/` → тікет `1234`). Якщо цифр немає — запитає вручну.

### Запуск

```
python -m scripts.publish
```

Скрипт проходить пронумеровані кроки. Перед кожною деструктивною дією
(створення гілки, пуш, запуск білдів у Codemagic) запитує підтвердження.

### Що саме відбувається, покроково

| Крок   | Що виконується                                                                                                                                                                                                                                                                                                                                                                |
|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0      | Завантажуємо конфіг + кеш. Виводимо номер тікета з назви теки. Тягнемо Jira-тікет і пов'язану IF-сторі. Виводимо summary-блок і чекаємо підтвердження. Завантажуємо `.p8`.                                                                                                                                                                                                    |
| 1-4, 6 | Редагуємо файли проєкту: `Config.xcconfig` (TEAM_ID, PRODUCT_BUNDLE_IDENTIFIER), `iosApp.xcodeproj/project.pbxproj` (те саме), `codemagic.yaml` (env-група `"9999"` → номер тікета), `iosApp/fastlane/Fastfile` (блок `app_review_information`), `gradle.properties` (секція `#Gradle`).                                                                                      |
| 5      | Перевіряємо `iosApp/fastlane/white/` на наявність скріншотів; якщо порожньо — чекаємо, поки закинете зображення.                                                                                                                                                                                                                                                              |
| 7      | Запускаємо `iosApp/fastlane/populate_locales_white.py` — копіює скріншоти в кожну локаль (з ресайзом, якщо треба).                                                                                                                                                                                                                                                            |
| —      | Шукаємо додаток у Codemagic (спочатку за ім'ям = номер тікета, потім номер-1). Виводимо GitHub-репозиторій з прив'язаного до Codemagic-додатку репо, щоб не набирати вручну.                                                                                                                                                                                                  |
| 8-9    | Створюємо гілку `ios_release`, додаємо `production` remote (`https://<user>:<token>@github.com/...`), комітимо все, пушимо.                                                                                                                                                                                                                                                   |
| 10     | Оновлюємо змінні середовища в Codemagic у групі з іменем = номер тікета: `APP_STORE_CONNECT_PRIVATE_KEY` (вміст `.p8`, секрет), `DEVELOPMENT_TEAM`, `GSHEET_TAB`, `GSHEET_ID`, `CM_APP_STORE_APPLE_ID`, `APP_STORE_CONNECT_KEY_IDENTIFIER`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_IDENTIFIER`, `APPLE_ID`.                                                                      |
| 11     | Запускаємо воркфлоу `ios_kmp_release` у Codemagic на гілці `ios_release` (білдить IPA, підписує, заливає в TestFlight).                                                                                                                                                                                                                                                       |
| 12     | Генеруємо текст для App Store через Claude Agent SDK (subtitle, description, keywords). Дублюємо вкладку `Template 2` у спільній таблиці в нову вкладку з іменем = номер тікета. Створюємо Telegraph-сторінку підтримки (закешований токен акаунта перевикористовується між запусками). Пишемо англомовний рядок у таблицю — переклади в решту 34 локалей таблиця рахує сама. |
| 12.5   | Чекаємо до ~2 хв, поки заповняться переклади, потім перевіряємо кожну локаль відповідно до лімітів App Store (name 30, subtitle 30, keywords 100, description 4000). Якщо у полі перевищують ≤3 локалей — правимо поштучно; якщо більше — скорочуємо англомовну версію і чекаємо повторного перекладу. До 3 ітерацій.                                                         |
| 13     | Запускаємо воркфлоу `upload_ios_metadata` у Codemagic. Він витягує таблицю, записує файли `fastlane/metadata/` і заливає їх разом зі скріншотами в App Store Connect.                                                                                                                                                                                                         |
| 14-16  | **Вручну.** Дозаповнити сторінку продукту в App Store Connect, відправити на рев'ю, увімкнути авторелiз.                                                                                                                                                                                                                                                                      |

Наприкінці скрипт друкує summary-табличку з клікабельними URL-ами на
Jira-тікет, IF-сторі, GitHub-репозиторій, Codemagic-додаток і обидва білди,
вкладку Google Sheet та Telegraph-сторінку підтримки. Також виконує
`git credential-manager github logout <user>`, щоб продакшн-акаунт не лишався
залогіненим.

### Кеш

`scripts/.publish-cache.json` (окремий для кожної пріли) зберігає:

- Username + PAT продакшн-акаунта GitHub
- API-токен Codemagic
- Access token Telegraph

Файл під `.gitignore` і переживає повторні запуски в межах одного проєкту —
після першого разу ці речі більше не запитуються. Видаліть файл, щоб знову
запитали.

---

## Відомі підводні камені

- **Ім'я теки проєкту має починатися з номера тікета.**
  Скрипт виводить номер тікета з початкових цифр у назві теки
  (напр. `1234-something/` → тікет `1234`). Якщо цифр немає — запитає.

- **Обов'язкова сусідня папка `Utils/`.** Іншим командам, що беруть цей
  шаблон, потрібен власний `../Utils/local.properties` або
  пропатчити `UTILS_LOCAL_PROPS` у `scripts/publish/config.py`, щоб він
  дивився в інше місце.


