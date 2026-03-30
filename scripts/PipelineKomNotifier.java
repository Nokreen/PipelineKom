package scripts;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;
import java.util.*;
import java.util.regex.*;

public class PipelineKomNotifier {
    
    private static final String BOT_NAME = "PipelineKom Bot";
    private static final String CONFIG_FILE = "scripts/bot.config";
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 2000;
    
    private static Logger logger;
    
    static {
        logger = new Logger();
    }
    
    public static void main(String[] args) {
        logger.info("========================================");
        logger.info("PipelineKom Notifier v2.0");
        logger.info("========================================");
        
        Properties config = loadConfig();
        
        String botToken = getBotToken(config);
        if (botToken == null) {
            logger.error("Не удалось получить токен бота");
            System.exit(1);
        }
        
        String chatId = getChatId(config);
        if (chatId == null) {
            logger.error("Не удалось получить ID чата");
            System.exit(1);
        }
        
        String repoName = System.getenv("GITHUB_REPOSITORY");
        String branchName = System.getenv("GITHUB_HEAD_REF");
        String prUrl = System.getenv("GITHUB_PR_URL");
        String prNumber = System.getenv("GITHUB_PR_NUMBER");
        String runId = System.getenv("GITHUB_RUN_NUMBER");
        String sha = System.getenv("GITHUB_SHA");
        
        logger.info("Исходные данные:");
        logger.info("  Репозиторий: " + (repoName != null ? repoName : "не указан"));
        logger.info("  Ветка: " + (branchName != null ? branchName : "не указана"));
        logger.info("  PR: " + (prNumber != null ? "#" + prNumber : "не указан"));
        logger.info("  SHA: " + (sha != null ? sha.substring(0, 7) : "не указан"));
        
        BuildInfo buildInfo = new BuildInfo(repoName, branchName, prUrl, prNumber, runId, sha);
        
        String version = determineVersion(buildInfo);
        logger.info("Сформирована версия: " + version);
        
        String timestamp = getCurrentTimestamp();
        
        String message = buildMessage(buildInfo, version, timestamp);
        
        boolean sent = sendWithRetry(botToken, chatId, message);
        
        if (sent) {
            logger.info("Уведомление успешно доставлено");
            System.exit(0);
        } else {
            logger.error("Не удалось отправить уведомление после " + MAX_RETRIES + " попыток");
            System.exit(1);
        }
    }
    
    private static Properties loadConfig() {
        Properties props = new Properties();
        props.setProperty("bot.name", BOT_NAME);
        props.setProperty("project.name", "PipelineKom");
        props.setProperty("default.owner", "Okreeon");
        
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                props.load(fis);
                logger.info("Загружена конфигурация из " + CONFIG_FILE);
            } catch (IOException e) {
                logger.warn("Не удалось загрузить " + CONFIG_FILE + ", использую значения по умолчанию");
            }
        } else {
            logger.info("Файл конфигурации не найден, использую значения по умолчанию");
        }
        
        return props;
    }
    
    private static String getBotToken(Properties config) {
        String token = System.getenv("PIPELINEKOM_TG_TOKEN");
        if (token == null || token.isEmpty()) {
            token = config.getProperty("bot.token");
        }
        
        if (token == null || token.isEmpty()) {
            logger.error("Переменная PIPELINEKOM_TG_TOKEN не установлена");
            return null;
        }
        
        Pattern tokenPattern = Pattern.compile("^\\d+:[-_a-zA-Z0-9]+$");
        if (!tokenPattern.matcher(token).matches()) {
            logger.error("Токен бота имеет неверный формат");
            return null;
        }
        
        return token;
    }
    
    private static String getChatId(Properties config) {
        String chatId = System.getenv("PIPELINEKOM_TG_CHAT");
        if (chatId == null || chatId.isEmpty()) {
            chatId = config.getProperty("bot.chatId");
        }
        
        if (chatId == null || chatId.isEmpty()) {
            logger.error("Переменная PIPELINEKOM_TG_CHAT не установлена");
            return null;
        }
        
        try {
            Long.parseLong(chatId);
        } catch (NumberFormatException e) {
            logger.error("Chat ID должен быть числом, получено: " + chatId);
            return null;
        }
        
        return chatId;
    }
    
    private static String determineVersion(BuildInfo info) {
        String fromEnv = System.getenv("RELEASE_VERSION");
        if (fromEnv != null && !fromEnv.isEmpty()) {
            logger.info("Использую версию из RELEASE_VERSION: " + fromEnv);
            return fromEnv;
        }
        
        if (info.sha != null && info.sha.length() >= 7) {
            String shortSha = info.sha.substring(0, 7);
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            String buildVersion = "build." + timestamp + "." + shortSha;
            logger.info("Сформирована версия на основе SHA: " + buildVersion);
            return buildVersion;
        }
        
        if (info.runNumber != null) {
            try {
                int num = Integer.parseInt(info.runNumber);
                String version = String.format("v%d.%d.%d", num / 100, (num % 100) / 10, num % 10);
                logger.info("Сформирована версия на основе run_number: " + version);
                return version;
            } catch (NumberFormatException e) {
                logger.warn("Не удалось распарсить run_number: " + info.runNumber);
            }
        }
        
        logger.warn("Не удалось определить версию, использую dev-SNAPSHOT");
        return "dev-SNAPSHOT";
    }
    
    private static String getCurrentTimestamp() {
        return LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
    }
    
    private static String buildMessage(BuildInfo info, String version, String timestamp) {
        String owner = info.owner != null ? info.owner : "Okreeon";
        String project = info.project != null ? info.project : "PipelineKom";
        String displayBranch = info.branch != null ? info.branch : "dev";
        String displayPrUrl = info.prUrl != null ? info.prUrl : "https://github.com/" + owner + "/" + project;
        String displayPrNumber = info.prNumber != null ? info.prNumber : "—";
        String shortSha = info.sha != null && info.sha.length() >= 7 ? info.sha.substring(0, 7) : "—";
        
        String statusEmoji = "✅";
        String statusText = "Успешно";
        
        return String.format("""
            %s <b>%s</b>
            
            🔄 <b>CI/CD Pipeline завершен</b>
            
            📦 <b>Проект:</b> %s
            🔖 <b>Версия:</b> <code>%s</code>
            🔗 <b>Коммит:</b> <code>%s</code>
            🕐 <b>Время:</b> %s
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>📂 Git</b>
            🌿 Ветка: <code>%s</code>
            🔗 PR: <a href=\"%s\">#%s</a>
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>🐳 Образ Docker</b>
            👤 Владелец: <code>%s</code>
            📦 Репозиторий: <code>%s</code>
            🏷️ Тег: <code>%s</code>
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>📊 Статистика сборки</b>
            🆔 Run ID: <code>%s</code>
            🤖 Runner: <code>ubuntu-latest</code>
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>%s Статус:</b> %s
            """,
            statusEmoji, BOT_NAME,
            project, version, shortSha, timestamp,
            displayBranch, displayPrUrl, displayPrNumber,
            owner, project, version,
            info.runNumber != null ? info.runNumber : "—",
            statusEmoji, statusText
        );
    }
    
    private static boolean sendWithRetry(String token, String chatId, String message) {
        int attempt = 1;
        while (attempt <= MAX_RETRIES) {
            logger.info("Попытка отправки " + attempt + " из " + MAX_RETRIES);
            
            int code = sendTelegramMessage(token, chatId, message);
            
            if (code == 200) {
                return true;
            } else if (code == 429) {
                logger.warn("Превышен лимит запросов, ждем " + RETRY_DELAY_MS + " мс");
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else if (code >= 500) {
                logger.warn("Ошибка сервера Telegram, код " + code);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                logger.error("Необрабатываемая ошибка, код " + code);
                break;
            }
            
            attempt++;
        }
        
        return false;
    }
    
    private static int sendTelegramMessage(String token, String chatId, String message) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setDoOutput(true);
            
            String escapedMessage = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
            
            String jsonBody = String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\",\"disable_web_page_preview\":true}",
                chatId, escapedMessage
            );
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
                os.flush();
            }
            
            int responseCode = conn.getResponseCode();
            
            if (responseCode != 200) {
                try (InputStream errorStream = conn.getErrorStream()) {
                    if (errorStream != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(errorStream));
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        logger.error("Ответ Telegram: " + errorResponse.toString());
                    }
                }
            }
            
            return responseCode;
            
        } catch (SocketTimeoutException e) {
            logger.error("Таймаут соединения: " + e.getMessage());
            return 408;
        } catch (IOException e) {
            logger.error("Ошибка ввода-вывода: " + e.getMessage());
            return 500;
        } catch (Exception e) {
            logger.error("Неожиданная ошибка: " + e.getMessage());
            return 500;
        }
    }
    
    static class BuildInfo {
        String owner;
        String project;
        String branch;
        String prUrl;
        String prNumber;
        String runNumber;
        String sha;
        
        BuildInfo(String repo, String branch, String prUrl, String prNumber, String runNumber, String sha) {
            if (repo != null && repo.contains("/")) {
                String[] parts = repo.split("/");
                this.owner = parts[0];
                this.project = parts[1];
            } else {
                this.owner = null;
                this.project = repo;
            }
            this.branch = branch;
            this.prUrl = prUrl;
            this.prNumber = prNumber;
            this.runNumber = runNumber;
            this.sha = sha;
        }
    }
    
    static class Logger {
        private static final String RESET = "\u001B[0m";
        private static final String RED = "\u001B[31m";
        private static final String GREEN = "\u001B[32m";
        private static final String YELLOW = "\u001B[33m";
        private static final String BLUE = "\u001B[34m";
        
        void info(String msg) {
            System.out.println(BLUE + "[INFO] " + RESET + msg);
        }
        
        void warn(String msg) {
            System.out.println(YELLOW + "[WARN] " + RESET + msg);
        }
        
        void error(String msg) {
            System.err.println(RED + "[ERROR] " + RESET + msg);
        }
    }
}
