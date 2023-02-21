package `in`.sunfox.healthcare.commons.android.sericom.interfaces

interface OnConnectionStateChangeListener {
    fun onDeviceConnect()
    fun onDeviceVerified()
    fun onReceivedData(data: String)
    fun onDeviceDisconnect()
    fun onConnectionError(errorMessage: String?)
}