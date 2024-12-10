package com.example.droid_share.data

enum class MessageType {
    FILE_PACK_DSCR,
    FILE,
    CANCEL_RX,
    PROGRESS_RX,
    RECEPTION_DONE,
    TX_REQUEST,
    ACCEPT_TX,
    DISMISS_TX,
}

fun isMessageControl(type: MessageType): Boolean {
    return when(type) {
        MessageType.FILE_PACK_DSCR,
        MessageType.FILE,
        MessageType.PROGRESS_RX -> {
            false
        }
        MessageType.CANCEL_RX,
        MessageType.RECEPTION_DONE,
        MessageType.TX_REQUEST,
        MessageType.ACCEPT_TX,
        MessageType.DISMISS_TX -> {
            true
        }
    }
}

enum class DataTransferState {
    IDLE,
    ACTIVE,
    DONE,
    READY_TO_RECEIVE,
    READY_TO_TRANSMIT,
    CANCEL_BY_TX,
    CANCEL_BY_RX,
    ERROR
}

fun isCancelState(state: DataTransferState) : Boolean {
    return (state == DataTransferState.CANCEL_BY_TX || state == DataTransferState.CANCEL_BY_RX)
}

enum class DataTransferStatus {
    DONE,
    CANCELED_BY_TX,
    CANCELED_BY_RX,
    ERROR
}

class DataTransferUtils {
}