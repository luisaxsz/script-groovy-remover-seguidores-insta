# 🧩 Instagram Follow Checker & Unfollower (Groovy + Selenium)

Scripts Groovy para **automatizar a análise e limpeza da sua conta do Instagram**, permitindo verificar quem **não te segue de volta** e, opcionalmente, **dar unfollow automático** em massa com segurança — tudo feito com login real, incluindo autenticação 2FA (código de dois fatores).


## 🔒 Segurança

- Nenhum dado é armazenado fora do seu computador.
- Login feito pelo **Chrome** controlado pelo Selenium, de forma legítima.
- O script aguarda manualmente a autenticação **2FA** e detecta o cookie de sessão.
- Todas as chamadas à API usam seus cookies reais.

## ⚠️ Avisos Importantes

- O uso de automação pode violar os **Termos de Serviço do Instagram**.
- Use por sua conta e risco e com moderação.
- Scripts para uso pessoal, educacional e análise de conta.
- Evite automações comerciais ou spam.
  
## ⚙️ Funcionalidades

### 1️⃣ `ig-check.groovy`
📊 **Função:**  
Realiza login na sua conta do Instagram, coleta as listas de **seguidores** e **seguidos**, e gera dois relatórios CSV:

- `nao_segue_de_volta.csv` → lista de pessoas que **você segue, mas que não te seguem de volta**  
  (colunas: `id`, `username`)
- `verificados.csv` → lista de perfis **verificados** que você segue  
  (colunas: `id`, `username`)

💡 Ele ignora automaticamente contas verificadas na contagem de “não segue de volta”.

### 2️⃣ `unfollow.groovy`
🚀 **Função:**  
Lê um arquivo CSV com IDs de usuários e executa requisições `POST` para o endpoint oficial da API do Instagram:
(https://www.instagram.com/api/v1/friendships/destroy/{id}/)

Ou seja — faz **unfollow automático** nos perfis listados.

O script usa a **mesma autenticação via Selenium**, garantindo que você está logado corretamente (inclusive com 2FA se necessário).

## 🧰 Requisitos

- **Java 11+**
- **Groovy 3+**
- **Google Chrome** instalado
- **ChromeDriver** compatível com sua versão do Chrome  

## 🔑 Variáveis de Ambiente

Antes de rodar qualquer script, defina suas credenciais do Instagram como variáveis de ambiente:

### Linux / macOS
```bash
export IG_USERNAME="seu_usuario"
export IG_PASSWORD="sua_senha"
```

## 🚀 Como Executar
### 1️⃣ Verificar quem não segue de volta
```bash 
groovy -Dgroovy.grape.report.downloads=true ig-check.groovy
```
### 📋 Durante a execução

- Será feito login no Instagram (pode ser necessário inserir o código 2FA).
- Feche manualmente quaisquer pop-ups (como “Salvar login” ou notificações).
- Após confirmar que está na tela inicial ou perfil, pressione **ENTER** no terminal.
- O script coletará suas listas de seguidores e seguidos via API do Instagram.
- Ao final, serão gerados dois arquivos CSV:
  - `nao_segue_de_volta.csv`
  - `verificados.csv`

**Caso você queira rodar mais que uma vez é necessario excluir os arquivos .csv antigos para os novos dados atualizarem**

### 2️⃣ Fazer Unfollow Automático

```bash
groovy -Dgroovy.grape.report.downloads=true unfollow.groovy
```

### 📋 Durante a execução

- O script solicitará o **caminho completo** do arquivo CSV.
- Será feito login no Instagram (pode ser necessário inserir o código 2FA).
- Feche manualmente quaisquer pop-ups (como “Salvar login” ou notificações).
- Após confirmar que está na tela inicial ou perfil, pressione **ENTER** no terminal.
- O script executará os unfollows usando o endpoint oficial do Instagram.


