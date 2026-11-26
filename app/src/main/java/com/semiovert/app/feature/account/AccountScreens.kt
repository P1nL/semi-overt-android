package com.semiovert.app.feature.account

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.PreviewValidation
import com.semiovert.app.core.data.ApiFailure
import com.semiovert.app.core.data.LocalBusiness
import com.semiovert.app.core.data.userMessage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AccountMode(val action: String,val busy: String) {
    LOGIN("登录","正在登录"),
    REGISTER("注册","正在注册"),
    RECOVERY("重置密码","正在重置密码"),
}

@Composable
private fun AccountField(
    label: String,value: String,onValue: (String)->Unit,error: String?=null,tag: String=label,
    keyboardType: KeyboardType=if(label.contains("邮箱"))KeyboardType.Email else KeyboardType.Text,
    trailingIcon: (@Composable ()->Unit)?=null,
    animateLabelOnEnter: Boolean=false,
    labelVisible: Boolean=true,
) {
    val spark=rememberSparkControl()
    StitchedTextField(value=value,onValueChange=onValue,
        label={ScrambleText(if(labelVisible)label else "",animateOnEnter=animateLabelOnEnter)},
        singleLine=true,isError=error!=null,interactionSource=spark.source,trailingIcon=trailingIcon,
        supportingText=if(error!=null) {{Text(error.withInterfaceDigits())}} else null,
        visualTransformation=if(keyboardType==KeyboardType.Number)InterfaceDigitTransformation else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=keyboardType),
        modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).then(spark.modifier).testTag(tag))
}

/** Login, registration and recovery share the same email and primary editor. */
@Composable
fun AccountForm(onEnterDemo: (String?)->Unit,initialMode: AccountMode=AccountMode.LOGIN) {
    val business = LocalBusiness.current
    val session = business?.session?.collectAsStateWithLifecycle()?.value
    var mode by rememberSaveable {mutableStateOf(initialMode)}
    var name by rememberSaveable {mutableStateOf("")}
    var email by rememberSaveable {mutableStateOf("")}
    // Credentials are intentionally not saveable and never leave this form.
    var secret by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    var sent by remember {mutableStateOf(false)}
    var attempted by rememberSaveable {mutableStateOf(false)}
    var emailAttempted by remember {mutableStateOf(false)}
    var notice by rememberSaveable {mutableStateOf("")}
    var submitting by remember {mutableStateOf(false)}
    var sending by remember {mutableStateOf(false)}
    var challenge by remember {mutableStateOf(false)}
    var cooldown by remember {mutableIntStateOf(0)}
    var serverError by remember {mutableStateOf<String?>(null)}
    val motion=LocalMotionEnabled.current
    val scope=rememberCoroutineScope()
    val transitionDuration=motionMillis(200)
    val submitTransition=updateTransition(submitting,label="account-submit-state")
    val keyboard=LocalSoftwareKeyboardController.current
    val focus=LocalFocusManager.current
    val register=mode==AccountMode.REGISTER
    val recovery=mode==AccountMode.RECOVERY
    val nameError=if(!register)null else if(business==null)PreviewValidation.name(name)
        else if(name.trim().length !in 3..32) "账号需为 3–32 个字符" else null
    val emailError=if(business!=null&&!register&&!recovery) {
        if(email.isBlank() || email.trim().length>120) "请输入账号或邮箱" else null
    } else PreviewValidation.email(email)
    val secretError=if(business==null)PreviewValidation.password(secret) else when {
        secret.isBlank() -> "请输入密码"
        secret.toByteArray(Charsets.UTF_8).size > 72 -> "密码最多 72 个 UTF-8 字节"
        (register || recovery) && secret.toByteArray(Charsets.UTF_8).size < 8 -> "密码至少 8 个 UTF-8 字节"
        else -> null
    }
    val confirmationError=if((register||recovery)&&secret!=confirmation)"两次密码不一致" else null
    val codeError=if(business!=null && (recovery || register)) {
        if(!code.matches(Regex("[0-9]{6}"))) "请输入 6 位验证码" else null
    } else if(recovery)when {
        !sent->"请先获取验证码"
        code!="123456"->"验证码有误"
        else->null
    } else null

    fun changeMode(next: AccountMode) {
        if(submitting||sending||next==mode)return
        focus.clearFocus();keyboard?.hide()
        if(mode==AccountMode.RECOVERY||next==AccountMode.RECOVERY) {
            secret="";confirmation=""
        }
        code="";sent=false;attempted=false;emailAttempted=false;notice="";serverError=null
        mode=next
    }
    BackHandler(mode!=AccountMode.LOGIN&&!submitting) {changeMode(AccountMode.LOGIN)}
    LaunchedEffect(cooldown) { if(cooldown>0) { delay(1000); cooldown-- } }
    fun sendCode(token: String?) {
        if (business == null || sending) return
        val address = email.trim()
        val registration = register
        sending = true
        serverError = null
        notice = ""
        scope.launch {
            try {
                business.requestCode(address, registration, token)
                sent = true
                cooldown = 60
                notice = if(registration) "验证码已发送" else "已注册邮箱将收到验证码"
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (error is ApiFailure && error.status == 429) {
                    error.retryAfter?.takeIf { it > 0 }?.let {
                        cooldown = it.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                    }
                }
                serverError = error.userMessage()
            } finally { sending = false }
        }
    }
    if (challenge && business != null) RegistrationChallenge(business.challengeSiteKey,
        onToken = { challenge = false; sendCode(it) }, onClose = { challenge = false })

    Column(Modifier.fillMaxWidth().testTag("login-screen").semantics {stateDescription=mode.action}) {
        if (session?.restoring == true) {
            RemoteState("恢复登录中")
            return@Column
        }
        if (session?.error != null && session.account == null) {
            RemoteState(session.error, action = "重试", onAction = { scope.launch { business?.restore() } })
        }
        AccountField(if(business==null||register||recovery)"邮箱" else "账号或邮箱",email,{
            if(!sending&&!submitting) { email=it;sent=false;code="";emailAttempted=false;notice="";serverError=null }
        },if(attempted||emailAttempted)emailError else null,tag="account-email",
            keyboardType=if(business==null||register||recovery)KeyboardType.Email else KeyboardType.Text)

        AnimatedVisibility(register,modifier=Modifier.stitchedRevealClip(),
            enter=expandVertically(tween(if(motion)360 else 0,easing=SoEase),expandFrom=Alignment.Top,clip=false)+
                fadeIn(tween(if(motion)180 else 0)),
            exit=shrinkVertically(tween(if(motion)280 else 0,easing=SoEase),shrinkTowards=Alignment.Top,clip=false)+
                fadeOut(tween(if(motion)200 else 0))) {
            AccountField(if(business==null)"昵称" else "账号",name,{if(!submitting)name=it},if(attempted)nameError else null,tag="account-name",
                animateLabelOnEnter=true,labelVisible=register)
        }
        AnimatedVisibility(recovery || (business != null && register),modifier=Modifier.stitchedRevealClip(),
            enter=expandVertically(tween(if(motion)360 else 0,easing=SoEase),expandFrom=Alignment.Top,clip=false)+
                fadeIn(tween(if(motion)180 else 0)),
            exit=shrinkVertically(tween(if(motion)280 else 0,easing=SoEase),shrinkTowards=Alignment.Top,clip=false)+
                fadeOut(tween(if(motion)200 else 0))) {
            AccountField("验证码",code,{if(!submitting)code=it},if(attempted)codeError else null,tag="recovery-code",
                keyboardType=KeyboardType.Number,animateLabelOnEnter=true,labelVisible=recovery||register,trailingIcon={
                    TextButton(onClick={
                        emailAttempted=true
                        if(emailError==null) {
                            emailAttempted=false
                            if(business==null)sent=true
                            else if(register&&business.challengeSiteKey.isNotBlank())challenge=true
                            else sendCode(null)
                        }
                    },enabled=!submitting&&!sending&&cooldown==0,colors=accentTextButtonColors(),modifier=Modifier.testTag("account-send-code")) {
                        ScrambleText(when {sending->"发送中";cooldown>0->"${cooldown}s";sent->"重新获取";else->"获取验证码"},
                            style=MaterialTheme.typography.labelSmall)
                    }
                })
        }
        StitchedPasswordPair(
            value=secret,onValueChange={if(!submitting){secret=it;notice="";serverError=null}},
            confirmation=confirmation,onConfirmationChange={if(!submitting)confirmation=it},
            label=if(recovery)"新密码" else "密码",confirmationLabel="确认密码",
            error=if(attempted)secretError else null,confirmationError=if(attempted)confirmationError else null,
            tag="account-password",confirmationTag="account-confirmation",
            showConfirmation=mode!=AccountMode.LOGIN)
        if(mode==AccountMode.LOGIN)Row(Modifier.fillMaxWidth().height(36.dp),horizontalArrangement=Arrangement.End,
            verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={changeMode(AccountMode.RECOVERY)},
                enabled=!submitting,contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),
                modifier=Modifier.testTag("account-recovery-toggle")) {
                ScrambleText("忘记密码",
                    style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(onClick={
            attempted=true
            if(!submitting&&!sending&&nameError==null&&emailError==null&&secretError==null&&confirmationError==null&&codeError==null) {
                val submittedName=if(register)name.trim() else null
                val submittedMode=mode
                submitting=true;keyboard?.hide();serverError=null;notice=""
                scope.launch {
                    try {
                        if(business==null) snapshotFlow {submitTransition.currentState&&!submitTransition.isRunning}.first {it}
                        else when(submittedMode) {
                            AccountMode.LOGIN -> business.signIn(email,secret,remember=true)
                            AccountMode.REGISTER -> business.register(name,email,secret,code)
                            AccountMode.RECOVERY -> business.reset(email,code,secret)
                        }
                    } catch(error: CancellationException) { throw error }
                    catch(error: Exception) {
                        serverError=error.userMessage()
                        return@launch
                    }
                    finally { submitting=false }
                    attempted=false;emailAttempted=false
                    focus.clearFocus()
                    secret="";confirmation="";code=""
                    if(submittedMode==AccountMode.RECOVERY) {
                        mode=AccountMode.LOGIN;sent=false
                        notice="密码已重置，请登录"
                    } else onEnterDemo(if(business==null)submittedName else null)
                }
            }
        },enabled=!submitting&&!sending,colors=ButtonDefaults.buttonColors(
            disabledContainerColor=MaterialTheme.colorScheme.primary,disabledContentColor=MaterialTheme.colorScheme.onPrimary),
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("account-submit").semantics {
                if(submitting)stateDescription=mode.busy
            }) {
            submitTransition.AnimatedContent(transitionSpec={
                (fadeIn(tween(transitionDuration))+scaleIn(tween(transitionDuration),initialScale=.75f)) togetherWith
                    (fadeOut(tween(transitionDuration))+scaleOut(tween(transitionDuration),targetScale=.75f))
            }) {loading ->
                if(loading)CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp,color=LocalContentColor.current)
                else ScrambleText(mode.action)
            }
        }
        serverError?.let { StatusText(it, Modifier.padding(top=12.dp).testTag("account-error"), color=MaterialTheme.colorScheme.error) }
        AnimatedVisibility(notice.isNotEmpty(),
            enter=expandVertically(tween(if(motion)280 else 0),clip=false)+fadeIn(tween(transitionDuration)),
            exit=shrinkVertically(tween(transitionDuration),clip=false)+fadeOut(tween(transitionDuration))) {
            Box(Modifier.fillMaxWidth().padding(top=12.dp).semantics {stateDescription=notice},contentAlignment=Alignment.Center) {
                ScrambleText(notice,style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.accentInk,animateOnEnter=true)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top=4.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp,Alignment.CenterHorizontally),
            verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={changeMode(if(register)AccountMode.LOGIN else AccountMode.REGISTER)},enabled=!submitting,colors=accentTextButtonColors(),
                modifier=(if(recovery)Modifier else Modifier.fillMaxWidth()).testTag("account-register-toggle")) {
                ScrambleText(when(mode) {
                    AccountMode.LOGIN->"没有账号？注册"
                    AccountMode.REGISTER->"已有账号？登录"
                    AccountMode.RECOVERY->"注册"
                })
            }
            if(recovery)TextButton(onClick={changeMode(AccountMode.LOGIN)},enabled=!submitting,colors=accentTextButtonColors(),
                modifier=Modifier.testTag("account-login-toggle")) {
                ScrambleText("登录")
            }
        }
    }
}

@Composable
private fun AccountPage(tag: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal=24.dp,vertical=32.dp).testTag(tag),content=content)
}

@Composable
fun RegistrationScreen(onEnterDemo: (String?)->Unit) {
    AccountPage("register-screen") {AccountForm(onEnterDemo,AccountMode.REGISTER)}
}

@Composable
fun ForgotPasswordScreen(onEnterDemo: (String?)->Unit) {
    AccountPage("forgot-screen") {AccountForm(onEnterDemo,AccountMode.RECOVERY)}
}
