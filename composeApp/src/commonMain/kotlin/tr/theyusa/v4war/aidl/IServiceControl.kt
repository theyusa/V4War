package tr.theyusa.v4war.aidl

expect interface IServiceControl {
    fun getStatus(): ServiceStatus
    fun registerObserver(observer: IServiceObserver?)
    fun unregisterObserver(observer: IServiceObserver?)
}
