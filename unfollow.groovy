@Grapes([
  @Grab(group='org.seleniumhq.selenium', module='selenium-java', version='4.11.0'),
  @Grab(group='org.slf4j', module='slf4j-simple', version='2.0.9')
])

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.support.ui.*
import java.time.Duration
import java.nio.file.*
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import java.util.concurrent.ThreadLocalRandom
import org.openqa.selenium.interactions.Actions

// ==========================================================
// ⚙️ CONFIGURAÇÕES INICIAIS ARQUIVO
// ==========================================================
BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))
print "\n📂 Digite o caminho completo do arquivo CSV (ex: /home/user/nao_segue_de_volta.csv): "
String csvPath = reader.readLine()?.trim()

File file = new File(csvPath)
if (!file.exists()) {
    println "❌ Arquivo '${csvPath}' não encontrado."
    System.exit(1)
}

// Lê o CSV e monta lista de IDs
List<Map> idsList = file.readLines()
    .drop(1) // remove cabeçalho
    .collect { line ->
        def parts = line.split(",", 2)
        [id: parts[0].trim(), username: parts.size() > 1 ? parts[1].trim() : ""]
    }
    .findAll { it.id }

println "\n📋 ${idsList.size()} usuários carregados de ${csvPath}."

// ==========================================================
// ⚙️ CONFIGURAÇÕES INICIAIS AUTENTICAÇÃO
// ==========================================================

String username = System.getenv("IG_USERNAME")
String password = System.getenv("IG_PASSWORD")
if (!username || !password) {
    println "❌ ERRO: configure IG_USERNAME e IG_PASSWORD nas variáveis de ambiente."
    System.exit(1)
}

System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")

long MIN_DELAY_MS = 2500
long MAX_DELAY_MS = 6000

ChromeOptions options = new ChromeOptions()
options.addArguments("--start-maximized")
options.addArguments("--disable-blink-features=AutomationControlled")
WebDriver driver = new ChromeDriver(options)
WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20))
Actions actions = new Actions(driver)

def rndDelay = { ->
    Thread.sleep(ThreadLocalRandom.current().nextLong(MIN_DELAY_MS, MAX_DELAY_MS + 1))
}

def clickAndWait = { WebElement el, long waitMs = 1200 ->
    try {
        actions.moveToElement(el).pause(200).click().perform()
        Thread.sleep(waitMs)
    } catch (e) {
        el.click()
        Thread.sleep(waitMs)
    }
}

// ==========================================================
// 1️⃣ LOGIN + 2FA
// ==========================================================
try {
    driver.get("https://www.instagram.com/accounts/login/")
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.name("username")))
    rndDelay()

    WebElement userInput = driver.findElement(By.name("username"))
    WebElement passInput = driver.findElement(By.name("password"))

    userInput.clear(); userInput.sendKeys(username)
    rndDelay()
    passInput.clear(); passInput.sendKeys(password)
    rndDelay()

    WebElement loginBtn = driver.findElements(By.xpath("//button[@type='submit']")).find { it.displayed }
    clickAndWait(loginBtn, 2000)

    println "\n⚠️  Se o Instagram solicitar o código 2FA, insira-o manualmente agora."
    println "   O script vai detectar automaticamente quando o login for concluído."
    println "   (Não precisa pressionar Enter, basta concluir a autenticação.)"

    def loginSessionCookie = null
    def maxWait = System.currentTimeMillis() + (1000 * 180)
    while (System.currentTimeMillis() < maxWait) {
        loginSessionCookie = driver.manage().getCookies().find { it.name == "sessionid" }
        if (loginSessionCookie && loginSessionCookie.value?.trim()) break
        Thread.sleep(2000)
    }

    if (!loginSessionCookie) {
        throw new RuntimeException("⚠️ Sessão não detectada. Verifique se a autenticação 2FA foi concluída.")
    }

    println "✅ Autenticação concluída, sessão ativa detectada!"
    rndDelay()

} catch (Exception e) {
    println "❌ Erro no login: ${e.message}"
    driver.quit()
    System.exit(1)
}

println "\n➡️ Aguarde o Instagram carregar completamente."
println "   Feche manualmente quaisquer pop-ups (salvar login, notificações etc.)."
println "   Assim que estiver na tela inicial ou perfil, pressione ENTER para continuar."
System.in.newReader().readLine()

// ==========================================================
// 2️⃣ CAPTURA COOKIES E SESSÃO
// ==========================================================
def cookies = driver.manage().getCookies()
def sessionCookie = cookies.find { it.name == "sessionid" }
def userIdCookie = cookies.find { it.name == "ds_user_id" }
def csrfCookie = cookies.find { it.name == "csrftoken" }

if (!sessionCookie || !userIdCookie || !sessionCookie.value?.trim()) {
    println "❌ Sessão perdida após fechar pop-ups. Refaça o login e tente novamente."
    driver.quit()
    System.exit(1)
}

def sessionId = sessionCookie.value
def userId = userIdCookie.value
def csrfToken = csrfCookie ? csrfCookie.value : ""

println "\n🔑 Sessão confirmada!"
println "   user_id: ${userId}"
println "   csrf: ${csrfToken ? 'OK' : 'N/A'}"
rndDelay()


// ==========================================================
// 3️⃣ EXECUÇÃO DOS UNFOLLOWS VIA API
// ==========================================================
println "\n🚀 Iniciando processo de unfollow..."
def baseUrl = "https://www.instagram.com/api/v1/friendships/destroy/"
int count = 0, success = 0, fail = 0

idsList.each { user ->
    count++
    def id = user.id
    def uname = user.username ?: "(sem username)"

    try {
        def url = new URL(baseUrl + id + "/")
        def conn = (HttpsURLConnection) url.openConnection()
        conn.setRequestMethod("POST")
        conn.setRequestProperty("Cookie", "sessionid=${sessionId}; ds_user_id=${userId}; csrftoken=${csrfToken}")
        conn.setRequestProperty("Referer", "https://www.instagram.com/")
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.setRequestProperty("X-IG-App-ID", "936619743392459")
        if (csrfToken) conn.setRequestProperty("X-CSRFToken", csrfToken)
        conn.setDoOutput(true)
        conn.connect()

        int code = conn.responseCode
        if (code == 200) {
            println "✅ (${count}/${idsList.size()}) Unfollow → ${uname} (${id})"
            success++
        } else {
            println "⚠️ (${count}/${idsList.size()}) Falha (${code}) → ${uname} (${id})"
            fail++
        }

        rndDelay()
    } catch (Exception e) {
        println "❌ (${count}/${idsList.size()}) Erro → ${uname} (${e.message})"
        fail++
    }
}

println "\n📊 Resumo:"
println "   ✅ Sucessos: ${success}"
println "   ⚠️ Falhas: ${fail}"
println "   🔚 Total processado: ${idsList.size()}"
println "\n✅ Processo concluído com segurança!"
driver.quit()
