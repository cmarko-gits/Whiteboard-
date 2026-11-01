package dev.whiteboard.app.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.whiteboard.core.cloud.AccountGateway
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import dev.whiteboard.ui.R
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AccountScreen { LOGIN, REGISTER, VERIFY, FORGOT, RESET, DELETE }
data class AccountUi(
    val screen: AccountScreen = AccountScreen.LOGIN,
    val busy: Boolean = false,
    val message: Int? = null,
    val error: Boolean = false,
    val formRevision: Int = 0
)

@HiltViewModel
class AccountViewModel @Inject constructor(private val gateway: AccountGateway) : ViewModel() {
    val account = gateway.account
    val available get() = gateway.available
    private val mutable = MutableStateFlow(AccountUi())
    val state = mutable.asStateFlow()

    init {
        work { gateway.restore() }
    }

    fun screen(screen: AccountScreen) {
        if (!mutable.value.busy) {
            mutable.update {
                it.copy(screen = screen, message = null, error = false, formRevision = it.formRevision + 1)
            }
        }
    }

    fun submit(email: String, password: String, code: String) {
        when (mutable.value.screen) {
            AccountScreen.LOGIN -> work {
                gateway.login(email, password)
                form(AccountScreen.LOGIN, R.string.account_signed_in)
            }
            AccountScreen.REGISTER -> work {
                gateway.register(email, password)
                form(AccountScreen.VERIFY, R.string.account_code_sent)
            }
            AccountScreen.VERIFY -> work {
                gateway.verify(code)
                form(AccountScreen.LOGIN, R.string.account_verified)
            }
            AccountScreen.FORGOT -> work {
                gateway.requestReset(email)
                form(AccountScreen.RESET, R.string.account_reset_sent)
            }
            AccountScreen.RESET -> work {
                gateway.reset(code, password)
                form(AccountScreen.LOGIN, R.string.account_password_reset)
            }
            AccountScreen.DELETE -> work {
                gateway.delete(password)
                form(AccountScreen.LOGIN, R.string.account_deleted)
            }
        }
    }

    fun resend(email: String) = work {
        gateway.resendVerification(email)
        form(AccountScreen.VERIFY, R.string.account_code_sent)
    }
    fun logout() = work {
        val revoked = gateway.logout()
        form(AccountScreen.LOGIN, if (revoked) R.string.account_signed_out else R.string.account_signed_out_offline)
    }

    private fun form(screen: AccountScreen, message: Int) {
        mutable.update { it.copy(screen = screen, message = message, error = false, formRevision = it.formRevision + 1) }
    }

    private fun work(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, message = null, error = false) }
        viewModelScope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when ((error as? CloudFailure)?.error) {
                    CloudError.UNAVAILABLE -> R.string.account_unavailable
                    CloudError.OFFLINE -> R.string.account_offline
                    CloudError.CREDENTIALS -> R.string.account_credentials_error
                    CloudError.CODE -> R.string.account_code_error
                    CloudError.REQUEST -> R.string.account_request_error
                    CloudError.LIMIT -> R.string.account_limit_error
                    CloudError.SECURE_STORAGE -> R.string.account_storage_error
                    else -> R.string.account_server_error
                }
                mutable.update { it.copy(message = message, error = true) }
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }
}
