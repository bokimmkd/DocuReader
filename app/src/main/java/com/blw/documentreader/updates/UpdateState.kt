package com.blw.documentreader.updates

/** Later suppresses only the current app visit, without a timed preference. */
fun shouldOfferUpdate(installed:Int,available:Int,deferredVersion:Int):Boolean =
    available>installed && available!=deferredVersion

fun shouldOfferRestart(available:Int,deferredVersion:Int):Boolean = available!=deferredVersion

/** Retained across activity recreation; Play consent is part of the same app visit. */
class UpdateOfferSession {
    private var foreground=false
    private var deferredUpdate=0
    private var deferredRestart=0
    var consentVersion=0
        private set
    fun startForeground() {
        if(!foreground) {deferredUpdate=0;deferredRestart=0}
        foreground=true
    }
    fun stopForeground(changingConfiguration:Boolean=false) {
        if(!changingConfiguration && consentVersion==0) foreground=false
    }
    fun showAgain() {deferredUpdate=0;deferredRestart=0}
    fun beginConsent(version:Int) {consentVersion=version}
    fun endConsent() {consentVersion=0}
    fun later(version:Int,ready:Boolean=false) {
        if(ready) deferredRestart=version else deferredUpdate=version
    }
    fun prompt(installed:Int,transfer:UpdateTransfer,available:Boolean,flexibleAllowed:Boolean):String? {
        val version=transfer.version
        if(consentVersion>0 || version<=maxOf(installed,transfer.completedVersion)) return null
        return when(transfer.stage) {
            UpdateStage.Ready -> if(shouldOfferRestart(version,deferredRestart)) "Ready" else null
            UpdateStage.Stopped -> if(available && shouldOfferUpdate(installed,version,deferredUpdate)) "Store" else null
            UpdateStage.Idle -> if(available && shouldOfferUpdate(installed,version,deferredUpdate))
                if(flexibleAllowed) "Available" else "Store" else null
            else -> null
        }
    }
}

/** Bounds an unanswered Play request and rejects callbacks from an old foreground session. */
class UpdateCheckGate(private val timeoutMillis:Long=15000L) {
    private var sequence=0L
    private var active:Pair<Long,Long>?=null
    fun begin(now:Long):Long? {
        val pending=active
        if(pending!=null && now-pending.second<timeoutMillis) return null
        return (++sequence).also {active=it to now}
    }
    fun complete(ticket:Long):Boolean {
        if(active?.first!=ticket) return false
        active=null
        return true
    }
    fun cancel() {active=null}
}

/** Play transfer state, independent of availability prompts and their Later preferences. */
enum class UpdateStage { Idle, Waiting, Downloading, Ready, Installing, Stopped, Installed }
data class UpdateTransfer(val version:Int=0,val stage:UpdateStage=UpdateStage.Idle,
                          val downloaded:Long=0,val total:Long=0,val completedVersion:Int=0) {
    val fraction:Float? get()=if(total>0) (downloaded.toDouble()/total).coerceIn(0.0,1.0).toFloat() else null
    fun observe(installed:Int,available:Int,next:UpdateStage,bytes:Long=0,totalBytes:Long=0):UpdateTransfer {
        if(available<=installed) return UpdateTransfer(completedVersion=completedVersion)
        if(available<=completedVersion) return this
        if(available<version || available==version && stage==UpdateStage.Installed) return this
        if(available==version && stage in listOf(UpdateStage.Waiting,UpdateStage.Downloading) && next==UpdateStage.Idle) return this
        if(available==version && stage in listOf(UpdateStage.Ready,UpdateStage.Installing) &&
            next in listOf(UpdateStage.Idle,UpdateStage.Waiting,UpdateStage.Downloading,UpdateStage.Ready)) return this
        return UpdateTransfer(available,next,bytes.coerceAtLeast(0),totalBytes.coerceAtLeast(0),if(next==UpdateStage.Installed) maxOf(completedVersion,available) else completedVersion)
    }
    fun beginInstall():UpdateTransfer = if(stage==UpdateStage.Ready) copy(stage=UpdateStage.Installing) else this
    fun installFailed():UpdateTransfer = if(stage==UpdateStage.Installing) copy(stage=UpdateStage.Ready) else this
}
