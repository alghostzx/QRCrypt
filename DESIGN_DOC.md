# Especificação do Projeto: QRCrypt

## Objetivo
Criar um aplicativo Android utilitário, 100% offline e de longo prazo ("compile and forget"), que funciona como uma camada transparente de criptografia para QR Codes usando o esquema personalizado `qrcrypt://`.

---

## 1. Fluxo de Descriptografia (Receptor de Intent)
- O aplicativo deve registrar um handler no sistema Android para o esquema de URI `qrcrypt://`.
- Ao escanear um QR code `qrcrypt://...` pela câmera nativa do celular (ex: Samsung) e tocar no link, o app deve abrir uma janela rápida/flutuante solicitando apenas a senha.
- Após descriptografar o conteúdo localmente, o app deve inspecionar o resultado e oferecer a ação adequada:
  - **URIs (`otpauth://`, `https://`, `http://`, `mailto:`, `geo:`, etc.):** Delegar diretamente ao sistema Android (`ACTION_VIEW`) para abrir no app correspondente (Bitwarden, Aegis, navegador, etc.).
  - **Configuração de Wi-Fi (`WIFI:...`):** Extrair os dados da rede (SSID, tipo de segurança, senha), exibir de forma legível e oferecer cópia rápida da senha ou atalho para conectar/abrir as configurações de Wi-Fi do sistema.
  - **Texto puro (notas, chaves de recuperação, senhas avulsas):** Exibir o texto na tela com botões para **Copiar** (marcando o dado como sensível na área de transferência do Android) e **Compartilhar** com outros apps (como apps de notas).

---

## 2. Fluxo de Criação e Conversão (App Aberto Avulso)
Quando aberto diretamente pelo ícone do aplicativo, deve oferecer:
- **Gerador por Templates:**
  - **Texto livre / Nota**
  - **2FA (TOTP):** Campos para Serviço, Conta e Chave Secreta (montando a URI `otpauth://` padrão).
  - **Wi-Fi:** Campos para SSID, Senha e Tipo de Segurança (montando a string `WIFI:` padrão), além de um botão para abrir as configurações de Wi-Fi do sistema caso o usuário queira visualizar o QR nativo do aparelho para re-empacotar.
  - **Link / URI**
- **Re-empacotar QR Code Existente ("Scan & Wrap"):**
  - Ler um QR code comum já existente — tanto apontando a **câmera** (ex: para a tela do PC) quanto selecionando uma **imagem salva na galeria** — pedir uma senha e gerar imediatamente a versão `qrcrypt://` daquele mesmo conteúdo.
- **Exportação:**
  - Exibir o QR Code gerado na tela com opções para **Salvar PNG na Galeria** ou **Compartilhar Imagem** diretamente.

---

## 3. Requisitos de Segurança e Privacidade
- **100% Offline:** O aplicativo não deve solicitar nem utilizar permissão de acesso à internet (`INTERNET`). Todo o processamento criptográfico e de leitura/geração de QR deve ocorrer localmente no dispositivo.
- **Criptografia Moderna e Autocontida:** O payload `qrcrypt://` deve conter todas as informações necessárias para descriptografia offline (versão, algoritmo, salt, IV/nonce e texto cifrado autenticado), usando derivação de chave resistente a força bruta a partir da senha do usuário.
- **Proteção de Tela e Memória:** Impedir capturas de tela e pré-visualizações na lista de apps recentes (`FLAG_SECURE`) quando segredos estiverem expostos, e limpar dados sensíveis (como arrays de senha) da memória após o uso.

---

## 4. Restrições de Ambiente e Build
- **Execução Exclusivamente via Linha de Comando (Headless):** Não baixe, instale ou dependa de ferramentas gráficas como o Android Studio.
- Configure o ambiente necessário via terminal (JDK + Android SDK Command-Line Tools, se ainda não estiverem presentes), gere uma keystore de assinatura de release e compile o APK final assinado (`app-release.apk`) pronto para instalação direta no celular.