package com.expiryx.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.material.progressindicator.LinearProgressIndicator

class LoginActivity : ThemedAppCompatActivity() {

    private var googleSignInClient: GoogleSignInClient? = null
    private var progressBar: LinearProgressIndicator? = null
    private var isForcedLogin: Boolean = false
    
    private val auth: FirebaseAuth? by lazy {
        try {
            FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e("LoginActivity", "Failed to get FirebaseAuth instance", e)
            null
        }
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken.isNullOrBlank()) {
                    setLoading(false)
                    Toast.makeText(this, "Missing ID token", Toast.LENGTH_LONG).show()
                    if (isForcedLogin) finish()
                    return@registerForActivityResult
                }
                firebaseAuthWithGoogle(idToken)
            } catch (e: ApiException) {
                setLoading(false)
                Log.e("LoginActivity", "Google sign in failed code=${e.statusCode}", e)
                Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                if (isForcedLogin) finish()
            }
        } else {
            setLoading(false)
            if (isForcedLogin) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isForcedLogin = intent.getBooleanExtra("force_login", false)
        
        super.onCreate(savedInstanceState)

        // 1. Configure Google early
        val gso = try {
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        } catch (e: Exception) {
            null
        }
        googleSignInClient = gso?.let { GoogleSignIn.getClient(this, it) }

        // 2. Navigation logic: Only skip if NOT a forced login
        if (!isForcedLogin) {
            if (AccountManager.isLoggedIn()) {
                navigateToMain()
                return
            }
            
            if (AccountManager.isWelcomeScreenPassed(this)) {
                navigateToMain()
                return
            }
        }

        setContentView(R.layout.activity_login)
        progressBar = findViewById(R.id.progressBarLogin)

        // 3. Style Branding (Expiry in default text color, X in Aqua)
        val welcomeText = findViewById<android.widget.TextView>(R.id.textViewWelcome)
        val text = "ExpiryX"
        val spannable = android.text.SpannableString(text)
        val aquaColor = getColor(R.color.teal_200)
        
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(aquaColor),
            text.length - 1, text.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        welcomeText.text = spannable

        // 4. Forced Login behavior: Trigger picker automatically, but keep UI in background
        if (isForcedLogin) {
            googleSignInClient?.let {
                setLoading(true)
                signInLauncher.launch(it.signInIntent)
            }
        }

        findViewById<Button>(R.id.buttonGoogleSignIn).setOnClickListener {
            googleSignInClient?.let {
                setLoading(true)
                signInLauncher.launch(it.signInIntent)
            }
        }

        findViewById<Button>(R.id.buttonContinue).setOnClickListener {
            AccountManager.setWelcomeScreenPassed(this, true)
            navigateToMain()
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar?.visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.buttonGoogleSignIn)?.isEnabled = !loading
        findViewById<Button>(R.id.buttonContinue)?.isEnabled = !loading
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth?.signInWithCredential(credential)
            ?.addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    AccountManager.setWelcomeScreenPassed(this, true)
                    AccountManager.startSync(this)
                    navigateToMain()
                } else {
                    setLoading(false)
                    Toast.makeText(this, "Auth failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    if (isForcedLogin) finish()
                }
            }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        startActivity(intent)
        finish()
    }
}
