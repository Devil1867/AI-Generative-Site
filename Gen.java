import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class Gen {

    private static final int PORT = System.getenv("PORT") != null 
        ? Integer.parseInt(System.getenv("PORT")) 
        : 9090;

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        server.createContext("/", new StaticFileHandler());
        server.createContext("/generate", new GenerateHandler());

        server.setExecutor(null);
        System.out.println("Server running at http://localhost:" + PORT);
        server.start();
    }

    static class StaticFileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) {
                path = "/Gen.html";
            }

            String fileName = path.startsWith("/") ? path.substring(1) : path;
            Path filePath = Paths.get(System.getProperty("user.dir"), fileName);

            if (!Files.exists(filePath)) {
                String errorMsg = "404 Not Found: " + filePath.toAbsolutePath();
                exchange.sendResponseHeaders(404, errorMsg.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(errorMsg.getBytes());
                }
                return;
            }

            String contentType = "text/plain";
            if (fileName.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (fileName.endsWith(".css")) contentType = "text/css; charset=UTF-8";
            else if (fileName.endsWith(".xml")) contentType = "application/xml; charset=UTF-8";

            byte[] bytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    static class GenerateHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStreamReader isr = new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8);
            BufferedReader br = new BufferedReader(isr);
            String rawFormData = br.readLine();

            Map<String, String> params = parseFormData(rawFormData);
            String prompt = params.getOrDefault("prompt", "");
            String resQuality = params.getOrDefault("res", "1024");
            String ratio = params.getOrDefault("ratio", "1:1");

            // Calculate pixel dimensions from ratio and resolution
            int baseDim = Integer.parseInt(resQuality);
            int width = baseDim;
            int height = baseDim;

            if ("3:4".equals(ratio)) {
                width = (int) (baseDim * 0.75);
                height = baseDim;
            } else if ("16:9".equals(ratio)) {
                width = baseDim;
                height = (int) (baseDim * 0.5625);
            }

            // Generate URL using calculated dimensions
            String encodedPrompt = java.net.URLEncoder.encode(prompt, StandardCharsets.UTF_8);
            String imageUrl = "https://image.pollinations.ai/prompt/" + encodedPrompt 
                            + "?width=" + width + "&height=" + height + "&nologo=true";

            String htmlResponse = buildHtml(prompt, imageUrl, width, height, ratio);
            byte[] bytes = htmlResponse.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }

        private Map<String, String> parseFormData(String formData) {
            Map<String, String> map = new HashMap<>();
            if (formData == null || formData.isEmpty()) return map;

            String[] pairs = formData.split("&");
            for (String pair : pairs) {
                String[] kv = pair.split("=");
                if (kv.length == 2) {
                    map.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
                }
            }
            return map;
        }

        private String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }

        private String buildHtml(String prompt, String imageUrl, int width, int height, String ratio) {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html lang='en'><head><meta charset='UTF-8'>");
            sb.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
            sb.append("<title>Helax Image Studio</title>");
            sb.append("<link rel='stylesheet' href='/Gen.css'></head><body>");
            sb.append("<canvas id='fireflies-canvas'></canvas>");
            sb.append("<div class='container'>");
            sb.append("<span class='badge'>Next-Gen Engine</span>");
            sb.append("<h1>AI Image Studio</h1>");
            sb.append("<p class='subtitle'>Turn plain language descriptions into high-fidelity visuals</p>");
            
            sb.append("<form action='/generate' method='post' id='genForm' onsubmit='startLoading(event)'>");
            sb.append("<div class='prompt-box'><div class='input-row'>");
            sb.append("<input type='text' name='prompt' id='promptInput' value='").append(escapeHtml(prompt)).append("' placeholder='Describe your image...' required>");
            sb.append("<button type='button' class='btn-icon' onclick='applySurprisePrompt()'>🎲 <span>Surprise</span></button>");
            sb.append("</div></div>");

            sb.append("<div class='tag-container'><span class='tag-label'>Enhance:</span>");
            sb.append("<span class='tag-chip' onclick=\"appendTag('cinematic lighting, 8k')\">+ Cinematic</span>");
            sb.append("<span class='tag-chip' onclick=\"appendTag('cyberpunk synthwave aesthetic')\">+ Cyberpunk</span>");
            sb.append("<span class='tag-chip' onclick=\"appendTag('photorealistic, sharp focus')\">+ Realistic</span>");
            sb.append("</div>");

            sb.append("<button type='submit' id='submitBtn' class='btn-primary'>Generate Again</button>");
            sb.append("</form>");

            if (!imageUrl.isEmpty()) {
                sb.append("<div class='result-box'>");
                sb.append("<p class='prompt-label'><strong>Prompt:</strong> ").append(escapeHtml(prompt)).append("</p>");
                sb.append("<div class='img-container'>");
                sb.append("<img id='genImg' src='").append(imageUrl).append("' alt='Generated Artwork' class='output-image'>");
                sb.append("</div>");
                sb.append("<div class='action-bar'>");
                sb.append("<span class='meta-stats'>Format: ").append(ratio).append(" (").append(width).append("x").append(height).append("px)</span>");
                sb.append("<button class='btn-download' onclick=\"downloadImage('").append(imageUrl).append("')\">⬇ Download High-Res</button>");
                sb.append("</div></div>");
            }
            // <-- INSERT SIGNATURE HERE
            sb.append("<footer class='creator-footer'>");
            sb.append("<span>Engineered with ⚡ by</span> ");
            sb.append("<a href='https://github.com/Devil1867' target='_blank' class='signature'>Hrituraj Deb</a>");
            sb.append("</footer>");
            
            sb.append("</div>");

            // Client scripts for downloading & animations
            sb.append("<script>");
            sb.append("const samplePrompts = ['A sleek modern workspace floating in a deep nebula atmosphere with neon emerald code lines', 'A solitary explorer with a backpack standing at the ridge of a misty mountain hill at twilight', 'A retro-futuristic vinyl disc dissolving into glowing neon dust particles'];");
            sb.append("function applySurprisePrompt() { const p = samplePrompts[Math.floor(Math.random()*samplePrompts.length)]; document.getElementById('promptInput').value = p; }");
            sb.append("function appendTag(t) { const el = document.getElementById('promptInput'); el.value = el.value.trim() ? el.value.trim() + ', ' + t : t; }");
            sb.append("async function downloadImage(url) {");
            sb.append("  try {");
            sb.append("    const res = await fetch(url); const blob = await res.blob();");
            sb.append("    const link = document.createElement('a'); link.href = URL.createObjectURL(blob);");
            sb.append("    link.download = 'ai-generation-' + Date.now() + '.png'; link.click();");
            sb.append("  } catch(e) { window.open(url, '_blank'); }");
            sb.append("}");

            // Canvas fireflies script for results page
            sb.append("const canvas = document.getElementById('fireflies-canvas'); const ctx = canvas.getContext('2d');");
            sb.append("function resCanvas(){ canvas.width = window.innerWidth; canvas.height = window.innerHeight; } window.onresize = resCanvas; resCanvas();");
            sb.append("let fList = Array.from({length: 40}, () => ({x: Math.random()*canvas.width, y: Math.random()*canvas.height, s: Math.random()*2+1, vx: (Math.random()-0.5)*0.5, vy: (Math.random()-0.5)*0.5, a: Math.random()*0.8+0.2, c: Math.random()>0.5?'6, 182, 212':'192, 132, 252'}));");
            sb.append("function loop(){ ctx.clearRect(0,0,canvas.width,canvas.height); fList.forEach(p => { p.x+=p.vx; p.y+=p.vy; if(p.x<0||p.x>canvas.width||p.y<0||p.y>canvas.height){ p.x=Math.random()*canvas.width; p.y=Math.random()*canvas.height; } ctx.beginPath(); ctx.arc(p.x, p.y, p.s, 0, Math.PI*2); ctx.fillStyle = 'rgba(' + p.c + ',' + p.a + ')'; ctx.shadowBlur = 8; ctx.shadowColor = 'rgb(' + p.c + ')'; ctx.fill(); }); requestAnimationFrame(loop); } loop();");
            sb.append("</script>");

            sb.append("</body></html>");
            return sb.toString();
        }
    }
}
