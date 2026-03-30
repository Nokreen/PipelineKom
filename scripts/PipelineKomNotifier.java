package scripts;

import java.io.*;
import java.net.*;
import java.time.*;
import java.time.format.*;

public class PipelineKomNotifier {
    
    private static final String BOT_NAME = "PipelineKom Bot";
    
    public static void main(String[] args) {
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String chatId = System.getenv("TELEGRAM_CHAT_ID");
        String repo = System.getenv("GITHUB_REPOSITORY");
        String branch = System.getenv("GITHUB_HEAD_REF");
        String prUrl = System.getenv("GITHUB_PR_URL");
        String prNumber = System.getenv("GITHUB_PR_NUMBER");
        String runNumber = System.getenv("GITHUB_RUN_NUMBER");
        String sha = System.getenv("GITHUB_SHA");
        
        if (botToken == null || chatId == null) {
            System.err.println("ERROR: TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID not set");
            System.exit(1);
        }
        
        String version = "v" + (runNumber != null ? runNumber : "0.1");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"));
        
        String owner = "Nokreen";
        String project = "PipelineKom";
        if (repo != null && repo.contains("/")) {
            owner = repo.split("/")[0];
            project = repo.split("/")[1];
        }
        
        String shortSha = (sha != null && sha.length() >= 7) ? sha.substring(0, 7) : "---";
        String displayBranch = (branch != null && !branch.isEmpty()) ? branch : "dev";
        String displayPrUrl = (prUrl != null) ? prUrl : "https://github.com/" + owner + "/" + project;
        String displayPrNumber = (prNumber != null) ? prNumber : "---";
        
        String message = String.format("""
            🤖 <b>%s</b>
            
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
            <b>✅ Статус:</b> Успешно
            """,
            BOT_NAME, project, version, shortSha, timestamp,
            displayBranch, displayPrUrl, displayPrNumber,
            owner, project, version
        );
        
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
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
                System.out.println("SUCCESS: Notification sent to Telegram");
            } else {
                System.err.println("ERROR: HTTP " + responseCode);
            }
            
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
