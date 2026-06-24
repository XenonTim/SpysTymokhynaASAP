# ASAP Messenger

Real-time месенджер «ASAP» Спис К.Д., Тимохина К.В.

## Структура фінального проєкту

```
ASAP-Messenger/
├── pom.xml
├── webapp/
│   ├── login.html
│   ├── register.html
│   ├── chat.html
│   ├── style.css
│   └── asap-client.js
└── src/
    ├── main/java/org/example/
    │   ├── server/network/
    │   ├── client/
    │   ├── db/
    │   ├── models/
    │   ├── shared/protocol/
    │   ├── shared/security/
    │   └── gateway/ 
    └── test/java/org/example/
```

## Налаштування MongoDB Atlas

`MongoDBConnection` тепер бере рядок підключення з системної властивості або
змінної середовища `MONGO_URI` (і опційно `MONGO_DB_NAME`), з фолбеком на
локальний `mongodb://localhost:27017`.

1. Створіть кластер на [MongoDB Atlas](https://www.mongodb.com/atlas) (є
   безкоштовний тариф M0) і користувача БД з паролем.
2. У Network Access додайте свою IP-адресу.
3. Скопіюйте рядок підключення (Drivers → Java)
4. Перед запуском кожного компонента (сервера, AdminSeeder) задайте змінну
   середовища:

   **Linux/macOS:**
   ```bash
   export MONGO_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority"
   ```

   **Windows (PowerShell):**
   ```powershell
   $env:MONGO_URI="mongodb+srv://<user>:<password>@<cluster>.mongodb.net/?retryWrites=true&w=majority"
   ```

   Або передайте напряму як `-D` параметр Maven (див. нижче).

## Запуск

Усі команди виконуються з кореня `ASAP-Messenger/` (там, де `pom.xml`).

### 0. Збірка

```bash
mvn clean install
```

Має пройти `mvn test` (юніт-тести AesUtil, PasswordUtil, PayloadBuilder,
SessionRegistry, SimpleJson) і зібрати `target/SpysTymokhynaASAP-1.0-SNAPSHOT.jar`.

### 1. Запустити основний сервер (термінал №1)

```bash
mvn exec:java -Dexec.mainClass="org.example.server.network.Server" -DMONGO_URI="mongodb+srv://..."
```

Побачите: `[Server] ASAP Messenger started on port 8080`.

### 2. Запустити веб-гейтвей (термінал №2)

```bash
mvn exec:java -Dexec.mainClass="org.example.gateway.WebGateway"
```

Побачите: `[WebGateway] Запущено на http://localhost:8081`.

### 3. Відкрити веб-клієнт

Перейдіть у браузері на **http://localhost:8081/login.html**.
