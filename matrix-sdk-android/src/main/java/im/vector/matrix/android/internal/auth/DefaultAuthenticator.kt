package im.vector.matrix.android.internal.auth

import com.squareup.moshi.Moshi
import im.vector.matrix.android.api.MatrixCallback
import im.vector.matrix.android.api.session.Session
import im.vector.matrix.android.api.auth.Authenticator
import im.vector.matrix.android.api.auth.CredentialsStore
import im.vector.matrix.android.api.auth.data.HomeServerConnectionConfig
import im.vector.matrix.android.api.util.Cancelable
import im.vector.matrix.android.internal.session.DefaultSession
import im.vector.matrix.android.internal.auth.data.Credentials
import im.vector.matrix.android.internal.auth.data.PasswordLoginParams
import im.vector.matrix.android.internal.network.executeRequest
import im.vector.matrix.android.internal.util.CancelableCoroutine
import im.vector.matrix.android.internal.util.MatrixCoroutineDispatchers
import im.vector.matrix.android.internal.util.map
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit

class DefaultAuthenticator(private val retrofitBuilder: Retrofit.Builder,
                           private val jsonMapper: Moshi,
                           private val coroutineDispatchers: MatrixCoroutineDispatchers,
                           private val credentialsStore: CredentialsStore) : Authenticator {

    override fun authenticate(homeServerConnectionConfig: HomeServerConnectionConfig, login: String, password: String, callback: MatrixCallback<Session>): Cancelable {
        val authAPI = buildAuthAPI(homeServerConnectionConfig)
        val job = GlobalScope.launch(coroutineDispatchers.main) {
            val loginParams = PasswordLoginParams.userIdentifier(login, password, "Mobile")
            val loginResult = executeRequest<Credentials> {
                apiCall = authAPI.login(loginParams)
                moshi = jsonMapper
                dispatcher = coroutineDispatchers.io
            }.map {
                it?.apply { credentialsStore.save(it) }
            }.map {
                DefaultSession(homeServerConnectionConfig)
            }
            loginResult.either({ callback.onFailure(it) }, { callback.onSuccess(it) })
        }
        return CancelableCoroutine(job)
    }

    private fun buildAuthAPI(homeServerConnectionConfig: HomeServerConnectionConfig): AuthAPI {
        val retrofit = retrofitBuilder.baseUrl(homeServerConnectionConfig.hsUri).build()
        return retrofit.create(AuthAPI::class.java)
    }


}