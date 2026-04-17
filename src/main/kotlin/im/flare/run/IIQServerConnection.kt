package im.flare.run

interface IIQServerConnection {
    val url: String
    val username: String
    val password: String
    val ignoreSslCertificate: Boolean
}
