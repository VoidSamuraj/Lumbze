package com.voidsamuraj.lumbze


import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.OnAudioFocusChangeListener
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import com.voidsamuraj.lumbze.db.UsersFirebaseDAO
import com.voidsamuraj.lumbze.ui.theme.LumbzeTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.IllegalStateException


class MainActivity : ComponentActivity() {
    private  val mazeViewModel: MazeViewModel by viewModels()
    private var width:Int=0
    private lateinit var auth:MyAuthentication
    lateinit var audioManager: AudioManager
    lateinit var playbackAttributes: AudioAttributes
    var audioFocusChangeListener =
        OnAudioFocusChangeListener { focusChange ->
            try {
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        mazeViewModel.musicPlayer.start()
                    }
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                        mazeViewModel.musicPlayer.pause()
                    }
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        mazeViewModel.musicPlayer.release()
                    }
                }
            }catch(_: IllegalStateException){ }
        }

    @SuppressLint("SourceLockedOrientationActivity")
    @ExperimentalAnimationApi
    override fun onCreate(savedInstanceState: Bundle?) {

        audioManager =  getSystemService(Context.AUDIO_SERVICE) as AudioManager
        playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        audioManager.requestAudioFocus(focusRequest)

        installSplashScreen().apply {
            setKeepOnScreenCondition {
                mazeViewModel.isLoading.value
            }
        }
        super.onCreate(savedInstanceState)
        auth=MyAuthentication(BuildConfig.firebase_auth_key,this)
        this.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT


        //if(!mazeViewModel.isMusicInit())
            mazeViewModel.musicPlayer = MediaPlayer.create(this, R.raw.one_harp)


        setContent {
            LumbzeTheme() {

                initRewardAdd(BuildConfig.game_services_add_reward_id,LocalContext.current)
                initLevelAdd(BuildConfig.game_services_add_identity_full_screen_id,LocalContext.current)

                width=with(LocalDensity.current){   LocalConfiguration.current.screenWidthDp.dp.toPx()}.toInt()
                mazeViewModel.maze.apply {
                    postData(
                        cellSize = mazeViewModel.cellSize,
                        canvasSideSize = width,
                        updateCellSize =mazeViewModel::setCellSize,
                        mazeViewModel=mazeViewModel,
                        screenWidth =with(LocalDensity.current){   LocalConfiguration.current.screenWidthDp.dp.toPx()}.toInt()
                    )

                    createMaze(mazeViewModel.rowsAmount.value)
                    mazeViewModel.setMazeBitmap(getMaze())
                    mazeViewModel.setBallBitmap(getBall(resources))
                }
                Navigation(mazeViewModel = mazeViewModel,
                    navController = rememberAnimatedNavController( ),
                    width = width,
                    auth = auth,
                    resources = resources)
            }
        }
        MobileAds.initialize(this)
    }


    override fun onStart() {
        super.onStart()
        mazeViewModel.setRepositoryAndSharedPreferences(application)

        mazeViewModel.setMusicOn(mazeViewModel.sharedPrefs?.getBoolean("is_music_on",true)?:true)
        try {
            mazeViewModel.musicPlayer.apply {
                isLooping=true
                if(mazeViewModel.isMusicOn.value) start()
            }
        }catch(_: IllegalStateException){ }

        mazeViewModel.isUserSignedIn.value=false

        auth.firebaseOnStartSetup(){
            CoroutineScope(Dispatchers.Default).launch {
                mazeViewModel.setFirebaseInRepository(
                    usersFirebaseDAO = UsersFirebaseDAO()
                )

                mazeViewModel.saveIdLocally(auth.getUser()?.uid)
                mazeViewModel.isUserSignedIn.value=true
                mazeViewModel.getUser().value?.name?.let {
                    mazeViewModel.currentUserName.value=it
                }
            }

        }
    }

    override fun onStop() {
        super.onStop()
        try {
            mazeViewModel.musicPlayer.pause()
        }catch(_: IllegalStateException){ }
    }

    override fun onDestroy() {
        mazeViewModel.detachFirebaseListener()
        super.onDestroy()
    }


    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        auth.onFirebaseResult(requestCode,data){

            CoroutineScope(Dispatchers.Default).launch {
                mazeViewModel.setRepositoryAndSharedPreferences(application)
                mazeViewModel.setFirebaseInRepository(
                    usersFirebaseDAO = UsersFirebaseDAO()
                )
                mazeViewModel.saveIdLocally(auth.getUser()?.uid)
                mazeViewModel.isUserSignedIn.value=true

                var name=mazeViewModel.getUser().value?.name?:"name"
                if(name=="")name="name"
                mazeViewModel.editCurrentUserName(name)

                delay(1000)
                auth.getUser()?.let {
                    mazeViewModel.repository?.synchronizeDatabase(it.uid)
                }
            }

        }
    }
    fun initRewardAdd(addId:String, context: Context/*, onSuccess:()->Unit*/){
        val adRequest: AdRequest = AdRequest.Builder().build()
        RewardedInterstitialAd.load(context,
            addId,
            adRequest, object : RewardedInterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d("ADDS", loadAdError.toString())
                    mazeViewModel.mRewardedAd=null
                }

                override fun onAdLoaded(rewardedAd: RewardedInterstitialAd) {
                    Log.d("ADDS", "Ad was loaded.")
                    mazeViewModel.mRewardedAd=rewardedAd
                    //  onSuccess()
                }
            })
    }
    fun initLevelAdd(addId:String, context: Context/*, onSuccess:()->Unit*/){
        val adRequest = AdRequest.Builder().build()

        InterstitialAd.load(context,addId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d("ADDS", adError.toString())
                mazeViewModel.mLevelAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                Log.d("ADDS", "Ad was loaded.")
                mazeViewModel.mLevelAd = interstitialAd
            }
        })
    }

}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    LumbzeTheme() {
    }
}
