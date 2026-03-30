package scripts;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

public class PipelineKomNotifier {
    
    private static final String BOT_NAME = "PipelineKom Bot";
    private static final String PROJECT_NAME = "PipelineKom";
    
    public static void main(String[] args) {
        String botToken = System.getenv("PIPELINEKOM_TG_TOKEN");
        String chatId = System.getenv("PIPELINEKOM_TG_CHAT");
        String repoName = System.getenv("GITHUB_REPOSITORY");
        String branchName = System.getenv("GITHUB_HEAD_REF");
        String prUrl = System.getenv("GITHUB_PR_URL");
        String prNumber = System.getenv("GITHUB_PR_NUMBER");
        String runId = System.getenv("GITHUB_RUN_NUMBER");
        
        if (botToken == null || botToken.isEmpty()) {
            System.err.println("[PipelineKom] Ошибка: не найден токен бота");
            System.exit(1);
        }
        
        if (chatId == null || chatId.isEmpty()) {
            System.err.println("[PipelineKom] Ошибка: не найден ID чата");
            System.exit(1);
        }
        
        String version = formatVersion(runId);
        String timestamp = getCurrentTimestamp();
        
        String owner = "Okreeon";
        String project = PROJECT_NAME;
        if (repoName != null && repoName.contains("/")) {
            owner = repoName.split("/")[0];
            project = repoName.split("/")[1];
        }
        
        String message = buildMessage(version, timestamp, branchName, prUrl, prNumber, owner, project);
        
        sendTelegramMessage(botToken, chatId, message);
    }
    
    private static String formatVersion(String runNumber) {
        if (runNumber == null) {
            return "v0.1.0";
        }
        int num = Integer.parseInt(runNumber);
        int major = num / 100;
        int minor = (num % 100) / 10;
        int patch = num % 10;
        return String.format("v%d.%d.%d", major, minor, patch);
    }
    
    private static String getCurrentTimestamp() {
        return LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
    }
    
    private static String buildMessage(String version, String timestamp, 
                                        String branch, String prUrl, String prNumber,
                                        String owner, String project) {
        
        String displayBranch = (branch != null && !branch.isEmpty()) ? branch : "dev";
        String displayPrUrl = (prUrl != null) ? prUrl : "https://github.com/Okreeon/PipelineKom";
        String displayPrNumber = (prNumber != null) ? prNumber : "---";
        
        return String.format("""
            🤖 <b>%s</b>
            
            🔄 <b>CI/CD Pipeline завершен</b>
            
            📦 <b>Проект:</b> %s
            🔖 <b>Версия:</b> %s
            🕐 <b>Время:</b> %s
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>📂 Git</b>
            🌿 Ветка: <code>%s</code>
            🔗 Pull Request: <a href=\"%s\">#%s</a>
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>🐳 Docker образ</b>
            👤 Владелец: %s
            📦 Репозиторий: %s
            🏷️ Тег: %s
            
            ━━━━━━━━━━━━━━━━━━━━━
            <b>✅ Статус:</b> Успешно
            """,
            BOT_NAME,
            project, version, timestamp,
            displayBranch, displayPrUrl, displayPrNumber,
            owner, project, version
        );
    }
    
    private static void sendTelegramMessage(String token, String chatId, String message) {
        try {
            URL url = new URL("https://api.telegram.org/bot" + token + "/sendMessage");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            String escapedMessage = message
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
            
            String jsonBody = String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"HTML\",\"disable_web_page_preview\":true}",
                chatId, escapedMessage
            );
            
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes("UTF-8"));
            }
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                System.out.println("[PipelineKom] Уведомление отправлено");
            } else {
                System.err.println("[PipelineKom] Ошибка HTTP: " + responseCode);
            }
            
        } catch (Exception e) {
            System.err.println("[PipelineKom] Ошибка отправки: " + e.getMessage());
        }
    }
}
