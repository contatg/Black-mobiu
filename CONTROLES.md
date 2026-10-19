# Correções dos controles

Base: RanggaCasper/Black-Android, commit ccc5a57fc55e2c642658f1cc945fc961ebf06cf5 (v1.3.1).

- O primeiro dedo do analógico mantém o movimento; outro dedo do lado esquerdo não assume o comando.
- Uma zona morta radial pequena evita andar com o polegar parado, mantendo a velocidade máxima na borda.
- A mira conserva movimentos menores que um pixel, sem arredondar cada evento de toque.
- Dá para arrastar a câmera pelo botão de mirar ou pelo botão direito de tiro.
- O tiro esquerdo fica independente da câmera. O dedo da área livre tem prioridade sobre dedos segurando tiro/mira.
- Toques rápidos nos botões são enviados pelo menos uma vez ao próximo relatório de entrada.
- Pausa, editor, segundo plano, perda de foco e cancelamentos soltam os comandos anteriores.
- Botões com áreas sobrepostas são escolhidos pela proximidade ao centro relativo ao tamanho.
- Tocar num espaço vazio do editor não seleciona mais o analógico.
- A opção **HOLD TO AIM** aparece em **TOUCH CONTROLS** e passa a funcionar com o toque.

Os layouts salvos, posições, tamanhos e sensibilidade continuam sendo lidos dos arquivos existentes.
Os presets de dois, três e quatro dedos continuam disponíveis.

## Testes

`bash tests/run-touch-tests.sh` executa eventos SDL no código real de `native/touch.cpp`, sem precisar do jogo.
O teste usa as mesmas versões dos cabeçalhos SDL3, ImGui e toml++ do aplicativo. Precisa de Git e g++ com C++20.
Para verificar acessos de memória e comportamento indefinido, use `BLACK_TEST_SANITIZE=1 bash tests/run-touch-tests.sh`.

Os testes cobrem movimento simultâneo com mira/tiro, disputa de dedos, movimentos finos, troca do dedo de mira,
toques curtos, cancelamento sem clique, pausa/retorno, identidades de dispositivos e sobreposição dos botões.
Não substituem testar a jogabilidade, o zoom e o layout em um celular.

## Compilar pelo GitHub

O workflow **Build** roda ao alterar o código na branch `main`, por uma tag `v*`, ou pelo botão **Run workflow**
em **Actions > Build**. O APK de uma execução da branch fica em **Artifacts**; apenas tags publicam Releases.
Os testes dos controles também rodam separadamente e não precisam de arquivo do jogo.

Para gerar o APK completo, o recompiler ainda precisa do `default.xbe` da versão USA do Xbox original.
O arquivo `ci/default.xbe.enc` herdado do autor está criptografado. A chave secreta dele não acompanha um fork.
O secret **XBE_KEY** precisa corresponder exatamente ao arquivo criptografado presente no seu fork.
Criar uma chave qualquer sem substituir o arquivo criptografado não resolve.

Para usar seu próprio executável, num terminal com OpenSSL, na pasta do projeto:

```bash
openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt -in assets/default.xbe -out ci/default.xbe.enc
```

O OpenSSL pede uma senha duas vezes. Cadastre essa mesma senha em
**Settings > Secrets and variables > Actions > New repository secret**, com o nome **XBE_KEY**.
Envie somente `ci/default.xbe.enc` atualizado para o fork e execute **Build** novamente.
O executável sem criptografia e os arquivos do jogo continuam fora do Git.

Se o GitHub mostrar que os workflows do fork estão desativados, habilite-os na aba **Actions**.
Sem um keystore próprio nos secrets, o workflow mantém a assinatura de depuração do projeto original.
Ela não corresponde à assinatura do APK oficial; não apague uma instalação com saves para tentar contornar isso.

## Validação no celular

Na página **TOUCH CONTROLS**, ajuste **TOUCH SENSITIVITY** e **HOLD TO AIM** conforme preferir.
Teste andar e arrastar a câmera enquanto mantém o tiro pressionado; depois abra a pausa e volte ao jogo.
O analógico e o tiro devem parar ao soltar os dedos, e a câmera não deve saltar ao trocar de dedo.
