@Grapes([
  @Grab(group='org.seleniumhq.selenium', module='selenium-java', version='4.11.0'),
  @Grab(group='org.slf4j', module='slf4j-simple', version='2.0.9'),
  @Grab(group='org.codehaus.groovy', module='groovy-json', version='3.0.19')
])

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.*
import java.time.Duration
import java.nio.file.*
import java.nio.charset.StandardCharsets
import java.util.concurrent.ThreadLocalRandom
import javax.net.ssl.HttpsURLConnection
import groovy.json.JsonSlurper

// ==========================================================
// 🔧 CONFIGURAÇÃO
// ==========================================================
String username = System.getenv("IG_USERNAME")
String password = System.getenv("IG_PASSWORD")
if (!username || !password) {
    println "❌ ERRO: configure IG_USERNAME e IG_PASSWORD nas variáveis de ambiente."
    System.exit(1)
}

System.setProperty("webdriver.chrome.driver", "/usr/bin/chromedriver")

String OUTPUT_NAO_SEGUE = "nao_segue_de_volta.csv"
String OUTPUT_VERIFICADOS = "verificados.csv"

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
// 1️⃣ LOGIN + 2FA (mantido igual)
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
// 3️⃣ COLETA DE USUÁRIOS VIA API
// ==========================================================
def coletarUsuariosViaAPI = { tipo ->
    def urlBase = "https://www.instagram.com/api/v1/friendships/${userId}/${tipo}/?count=50"
    def usuarios = [:] // username -> [id, verified]
    def jsonParser = new JsonSlurper()
    def nextMaxId = null

    println "\n🔍 Coletando lista de ${tipo} via API..."

    while (true) {
        def apiUrl = nextMaxId ? "${urlBase}&max_id=${URLEncoder.encode(nextMaxId, 'UTF-8')}" : urlBase
        println "➡️ Requisitando: ${apiUrl}"

        def connection = (HttpsURLConnection) new URL(apiUrl).openConnection()
        connection.setRequestMethod("GET")
        connection.setRequestProperty("Cookie", "sessionid=${sessionId}; ds_user_id=${userId}; csrftoken=${csrfToken}")
        connection.setRequestProperty("Referer", "https://www.instagram.com/")
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        connection.setRequestProperty("X-IG-App-ID", "936619743392459")
        if (csrfToken) connection.setRequestProperty("X-CSRFToken", csrfToken)
        connection.connect()

        if (connection.responseCode != 200) {
            println "⚠️ Erro ${connection.responseCode} ao acessar ${apiUrl}"
            break
        }

        def resp = connection.inputStream.text
        def data = jsonParser.parseText(resp)

        data.users.each { user ->
            def uname = user.username.toString()
            def uid = user.id?.toString() ?: "0"
            def verified = user.is_verified ?: false
            usuarios[uname] = [id: uid, verified: verified]
        }

        println "📦 ${usuarios.size()} ${tipo} coletados até agora..."
        nextMaxId = data.next_max_id
        if (!nextMaxId) break
        Thread.sleep(2000 + ThreadLocalRandom.current().nextInt(0, 2000))
    }

    println "✅ Total final de ${tipo}: ${usuarios.size()}"
    return usuarios
}

// ==========================================================
// 4️⃣ EXECUTA COLETA E COMPARAÇÃO
// ==========================================================
def followers = coletarUsuariosViaAPI("followers")
def following = coletarUsuariosViaAPI("following")

def verificados = following.findAll { _, data -> data.verified }
def naoSegueDeVolta = following.findAll { uname, data ->
    !followers.containsKey(uname) && !data.verified
}

println "\n🚫 Não te seguem de volta (${naoSegueDeVolta.size()}):"
println naoSegueDeVolta.keySet().join("\n")

println "\n✅ Usuários verificados (${verificados.size()}):"
println verificados.keySet().join("\n")

// ==========================================================
// 5️⃣ SALVA RESULTADOS EM DOIS CSVs SEPARADOS
// ==========================================================
def salvarCsv = { arquivo, mapa ->
    def linhas = ["id,username"]
    mapa.each { uname, data ->
        linhas << "${data.id},${uname}"
    }
    Files.write(Paths.get(arquivo), linhas, StandardCharsets.UTF_8)
    println "💾 CSV salvo com sucesso: ${arquivo}"
}

salvarCsv(OUTPUT_NAO_SEGUE, naoSegueDeVolta)
salvarCsv(OUTPUT_VERIFICADOS, verificados)

println "\n✅ Execução finalizada com sucesso!"
