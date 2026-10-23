package com.semiovert.app.feature.account

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import com.semiovert.app.core.designsystem.*
import com.semiovert.app.core.model.PreviewValidation

@Composable
private fun AccountField(label: String,value: String,onValue: (String)->Unit,error: String?=null,password: Boolean=false,tag: String=label) {
    var visible by remember {mutableStateOf(false)}
    val spark=rememberSparkControl()
    OutlinedTextField(value=value,onValueChange=onValue,label={Text(label)},singleLine=true,isError=error!=null,interactionSource=spark.source,
        supportingText=if(error!=null) {{Text(error)}} else null,
        visualTransformation=if(password&&!visible)PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions=KeyboardOptions(keyboardType=if(password)KeyboardType.Password else if(label.contains("邮箱"))KeyboardType.Email else KeyboardType.Text),
        trailingIcon=if(password) {{PasswordToggleButton(visible,{visible=it},Modifier.testTag("$tag-visibility"))}} else null,
        modifier=Modifier.fillMaxWidth().padding(bottom=12.dp).animateContentSize(tween(motionMillis(180),easing=SoEase)).then(spark.modifier).testTag(tag))
}

/** Shared form: the guest profile owns login, while registration remains a secondary page. */
@Composable
fun AccountForm(register: Boolean,onOther: ()->Unit,onForgot: ()->Unit,onEnterDemo: (String?)->Unit) {
    var name by rememberSaveable {mutableStateOf("")}
    var email by rememberSaveable {mutableStateOf("")}
    // Credentials are intentionally not saveable and never leave this form.
    var password by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var attempted by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    val nameError=if(register)PreviewValidation.name(name) else null
    val emailError=PreviewValidation.email(email)
    val passwordError=PreviewValidation.password(password)
    val confirmationError=if(register&&password!=confirmation)"两次演示密码不一致" else null
    Column(Modifier.fillMaxWidth().testTag(if(register)"register-form" else "login-screen")) {
        DemoNotice("离线演示，不连接真实账号。请勿输入真实密码。")
        if(register)AccountField("昵称",name,{name=it},if(attempted)nameError else null,tag="account-name")
        AccountField("邮箱",email,{email=it},if(attempted)emailError else null,tag="account-email")
        AccountField("演示密码",password,{password=it},if(attempted)passwordError else null,password=true,tag="account-password")
        if(register)AccountField("确认演示密码",confirmation,{confirmation=it},if(attempted)confirmationError else null,password=true,tag="account-confirmation")
        if(!register)TextButton(onClick=onForgot,modifier=Modifier.fillMaxWidth()) {Text("忘记密码")}
        Button(onClick={
            attempted=true
            if(nameError==null&&emailError==null&&passwordError==null&&confirmationError==null) {
                password="";confirmation="";keyboard?.hide();onEnterDemo(if(register)name.trim() else null)
            }
        },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("account-submit")) {Text(if(register)"创建演示身份" else "进入演示账号")}
        TextButton(onClick=onOther,modifier=Modifier.fillMaxWidth().padding(top=8.dp)) {Text(if(register)"已有账号，去登录" else "创建账号")}
        TextButton(onClick={name="离线读者";email="reader@example.invalid";password="demo-only-123";confirmation=password},modifier=Modifier.fillMaxWidth()) {Text("填入示例资料")}
    }
}

@Composable
private fun AccountPage(tag: String,content: @Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState())
        .padding(horizontal=24.dp,vertical=32.dp).testTag(tag),content=content)
}

@Composable
fun RegistrationScreen(onLogin: ()->Unit,onEnterDemo: (String?)->Unit) {
    AccountPage("register-screen") {AccountForm(true,onLogin,{},onEnterDemo)}
}

/** Email, demo code, new password and result stay in one route. */
@Composable
fun ForgotPasswordScreen(onDone: ()->Unit) {
    var email by rememberSaveable {mutableStateOf("")}
    var code by remember {mutableStateOf("")}
    var password by remember {mutableStateOf("")}
    var confirmation by remember {mutableStateOf("")}
    var sent by remember {mutableStateOf(false)}
    var attempted by remember {mutableStateOf(false)}
    var completed by rememberSaveable {mutableStateOf(false)}
    val keyboard=LocalSoftwareKeyboardController.current
    AccountPage("forgot-screen") {
        if(completed) {
            Text("演示重置已完成",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(bottom=12.dp))
            Text("未修改真实账号密码。",style=MaterialTheme.typography.bodyMedium)
            val spark=rememberSparkControl()
            Button(onClick=onDone,interactionSource=spark.source,modifier=Modifier.fillMaxWidth().padding(top=24.dp).heightIn(min=52.dp).then(spark.modifier)) {Text("返回个人页")}
        }else {
            DemoNotice("离线重置演示，不发送邮件，也不修改真实账号。")
            AccountField("邮箱",email,{email=it;sent=false;code="";attempted=false},if(attempted)PreviewValidation.email(email) else null,tag="recovery-email")
            val codeSpark=rememberSparkControl()
            TextButton(onClick={attempted=true;if(PreviewValidation.email(email)==null){sent=true;attempted=false}},interactionSource=codeSpark.source,modifier=Modifier.fillMaxWidth().then(codeSpark.modifier)) {Text("获取演示验证码")}
            if(sent)Text("演示验证码：123456",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(bottom=12.dp))
            val codeError=when { !sent->"请先获取演示验证码";code!="123456"->"请输入上方的演示验证码";else->null }
            AccountField("验证码",code,{code=it},if(attempted)codeError else null,tag="recovery-code")
            AccountField("新演示密码",password,{password=it},if(attempted)PreviewValidation.password(password) else null,password=true,tag="reset-password")
            AccountField("确认新密码",confirmation,{confirmation=it},if(attempted&&confirmation!=password)"两次演示密码不一致" else null,password=true,tag="reset-confirmation")
            val resetSpark=rememberSparkControl()
            Button(interactionSource=resetSpark.source,onClick={
                attempted=true
                if(PreviewValidation.email(email)==null&&codeError==null&&PreviewValidation.password(password)==null&&password==confirmation) {
                    password="";confirmation="";code="";keyboard?.hide();completed=true
                }
            },modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).then(resetSpark.modifier)) {Text("完成演示重置")}
        }
    }
}
