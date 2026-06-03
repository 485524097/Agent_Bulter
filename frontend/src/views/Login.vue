<template>
    <div class="login-page">
        <div class="login-card">
            <h2>Butler AI 个人财务管家</h2>
            <p class="subtitle">手机号验证码登录</p>

            <el-form label-position="top">
                <el-form-item label="手机号">
                    <el-input
                        v-model="mobile"
                        placeholder="请输入手机号"
                        maxlength="11"
                    />
                </el-form-item>

                <el-form-item label="验证码">
                    <div class="code-row">
                        <el-input
                            v-model="code"
                            placeholder="请输入验证码"
                        />
                        <el-button
                        :disabled="countdown > 0"
                        @click="handleSendCode"
                        >
                        {{ countdown > 0 ? countdown + 's' : '发送验证码' }}
                    </el-button>
        </div>
    </el-form-item>

    <el-button
        type="primary"
        class="login-button"
    :loading="loading"
    @click="handleLogin"
    >
    登录
</el-button>
</el-form>
</div>
</div>
</template>

<script setup>
    import { ref } from 'vue'
    import { ElMessage } from 'element-plus'
    import { useRouter } from 'vue-router'
    import { sendSmsCode, loginBySms } from '../api/auth'

    const router = useRouter()

    const mobile = ref('13300000000')
    const code = ref('')
    const loading = ref(false)
    const countdown = ref(0)

    let timer = null

    const handleSendCode = async () => {
    if (!mobile.value) {
    ElMessage.warning('请输入手机号')
    return
}

    await sendSmsCode({
    mobile: mobile.value,
    scene: 'LOGIN'
})

    ElMessage.success('验证码已发送，请查看后端控制台')

    countdown.value = 60
    timer = setInterval(() => {
    countdown.value--

    if (countdown.value <= 0) {
    clearInterval(timer)
}
}, 1000)
}

    const handleLogin = async () => {
    if (!mobile.value || !code.value) {
    ElMessage.warning('请输入手机号和验证码')
    return
}

    loading.value = true

    try {
    const res = await loginBySms({
    mobile: mobile.value,
    scene: 'LOGIN',
    code: code.value
})

    localStorage.setItem('butler_token', res.token)
    localStorage.setItem('butler_user_id', res.userId)
    localStorage.setItem('butler_mobile', res.mobile)

    ElMessage.success('登录成功')
    router.push('/chat')
} finally {
    loading.value = false
}
}
</script>

<style scoped>
    .login-page {
    height: 100vh;
    background: #f5f7fb;
    display: flex;
    align-items: center;
    justify-content: center;
}

    .login-card {
    width: 380px;
    padding: 32px;
    border-radius: 12px;
    background: #ffffff;
    box-shadow: 0 8px 30px rgba(0, 0, 0, 0.08);
}

    .login-card h2 {
    margin: 0;
    text-align: center;
}

    .subtitle {
    text-align: center;
    color: #888;
    margin-bottom: 28px;
}

    .code-row {
    display: flex;
    gap: 10px;
    width: 100%;
}

    .login-button {
    width: 100%;
    margin-top: 10px;
}
</style>