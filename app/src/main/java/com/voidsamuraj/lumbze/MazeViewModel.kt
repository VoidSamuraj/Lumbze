package com.voidsamuraj.lumbze

import android.animation.ValueAnimator
import android.app.Application
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaPlayer
import androidx.activity.ComponentActivity
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.voidsamuraj.lumbze.db.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Singleton

@Singleton
class MazeViewModel():ViewModel() {
    //////////USER LOGIC
    val maze: Maze by lazy { Maze {
        CoroutineScope(Dispatchers.IO).launch {
            addPoints()
            currentUserPoints.value+=maze.allCells
            mazePoints = maze.allCells

        }
    }
    }
    companion object {
        enum class BallStyle(val resId:Int) {
            NORMAL(R.drawable.ball_blue),
            SILVER(R.drawable.ball_silver),
            GOLD(R.drawable.ball_gold),
            RED(R.drawable.ball_red),
            GREEN(R.drawable.ball_green),
            PURPLE(R.drawable.ball_purple),
            LOLLIPOP(R.drawable.ball_lollipop),
            BLACK_HOLE(R.drawable.ball_black_hole),
        }
        suspend fun getIfContainAndMessageIdForBall(ball:BallStyle,mazeViewModel: MazeViewModel):Pair<Boolean,Int>{
            val user=mazeViewModel.getUser().value
            val containsBall=user?.unlocked_balls?.contains(ball.resId)?:false
            val points=user?.points?:0
            val pair= when(ball){
                BallStyle.NORMAL->Pair(true,-1)
                BallStyle.SILVER->Pair(containsBall||points>400,R.string.SILVER)
                BallStyle.GOLD->Pair(containsBall||points>1000,R.string.GOLD)
                BallStyle.RED->Pair(containsBall||points>800,R.string.RED)
                BallStyle.GREEN->Pair(containsBall||points>2000,R.string.GREEN)
                BallStyle.PURPLE->Pair(containsBall||points>5000,R.string.PURPLE)
                BallStyle.LOLLIPOP->Pair(containsBall||points>10000,R.string.LOLLIPOP)
                BallStyle.BLACK_HOLE->Pair(containsBall||points>15000,R.string.BLACK_HOLE)
            }
            if(!containsBall && pair.first)
                user?.let {
                    it.unlocked_balls.add(ball.resId)
                    CoroutineScope(Dispatchers.IO).launch {
                        mazeViewModel.repository?.addUser(it)
                    }

                }
            return  pair
        }
    }

    var mRewardedAd: RewardedInterstitialAd? = null
    var mLevelAd:  InterstitialAd? = null
    var canPlayLevelAdd:Boolean =false


    private val userStats:MutableState<List<Pair<Int,User>>?> = mutableStateOf(null)
    val isUserSignedIn= mutableStateOf(false)
    private val currentUser:MutableState<User?> = mutableStateOf(null)
    val currentUserName:MutableState<String> = mutableStateOf("")
    val currentUserPoints:MutableState<Int> = mutableStateOf(-1)

    var sharedPrefs:SharedPreferences?=null
    var repository:LumbzeRepositoryImplementation?=null

    var mazePoints:Int = 0

    lateinit var musicPlayer:MediaPlayer
    private val _isMusicOn:MutableState<Boolean> = mutableStateOf(true)
    val isMusicOn:State<Boolean> = _isMusicOn

    /**
    @param default neg value
    @return value of isMusicOn
     * */
    fun setMusicOn(isOn: Boolean = !isMusicOn.value):Boolean{
        _isMusicOn.value=isOn

        sharedPrefs?.edit()!!.putBoolean("is_music_on",isOn).apply()
        return isOn
    }

    private val  _cellSize:MutableState<Int> = mutableStateOf(40)
    val cellSize:State<Int> =_cellSize
    fun setCellSize(size:Int){
        _cellSize.value=size
        sharedPrefs?.edit()?.putInt("cell_size",size)?.apply()
    }

    private val _mazeBitmap:MutableState<Bitmap?> = mutableStateOf(null)
    val mazeBitmap:State<Bitmap?> = _mazeBitmap
    fun setMazeBitmap(bitmap: Bitmap){_mazeBitmap.value=bitmap}

    private val _ballBitmap:MutableState<Bitmap?> = mutableStateOf(null)
    val ballBitmap:State<Bitmap?> = _ballBitmap
    fun setBallBitmap(bitmap: Bitmap){_ballBitmap.value=bitmap}

    private val  _isDrawerOpen: MutableState<Boolean> = mutableStateOf(false)
    val isDrawerOpen:State<Boolean> = _isDrawerOpen
    fun setDrawerOpen(boolean: Boolean){_isDrawerOpen.value=boolean}

    private val  _isScreenTouchable: MutableState<Boolean> = mutableStateOf(true)
    val isScreenTouchable:State<Boolean> = _isScreenTouchable
    fun setIsScreenTouchable(boolean: Boolean){_isScreenTouchable.value=boolean}

    private val  _rotation:MutableState<Float> = mutableStateOf(0.0f)
    val rotation:State<Float> =_rotation
    fun setRotation(angle:Float){_rotation.value=angle}

    private val  _translation:MutableState<Float> = mutableStateOf(0.0f)
    val translation:State<Float> =_translation
    fun setTranslation(move:Float){_translation.value=move}

    private var _rowsAmount:MutableState<Int> = mutableStateOf(15)
    val rowsAmount:State<Int> = _rowsAmount
    fun setRowsAmount(amount: Int){
        _rowsAmount.value=amount
        sharedPrefs?.edit()?.putInt("rows_amount",amount)?.apply()
        resetPositionAndRotation()
    }

    fun getBallStyle():Int{
        return  sharedPrefs?.getInt("BALL_STYLE",BallStyle.NORMAL.resId)?:BallStyle.NORMAL.resId
    }
    fun setBallStyle(ballStyle: BallStyle){
        sharedPrefs?.edit()?.putInt("BALL_STYLE",ballStyle.resId)?.apply()
        //forcing redraw of view
        val oldVal=cellSize.value
        _cellSize.value=oldVal+1
        _cellSize.value=oldVal
    }

    fun saveIdLocally(uId:String?){
        uId?.let { sharedPrefs?.edit()!!.putString("uId",it).apply()}
    }

    // for splash screen
    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()
    init {
        viewModelScope.launch()  {
            delay(1000L)
            _isLoading.value = false
        }
    }


    private suspend fun addPoints(){
        val id= sharedPrefs?.getString("uId","")
        id?.let {
            if (it!=""&&isUserSignedIn.value){
                var user = repository!!.getUser(it)
                if (user == null)
                    user = User(it, "Name", 0, arrayListOf())
                user.let {
                    repository!!.addUser(
                        User(
                            id = it.id,
                            name = it.name,
                            points = it.points + maze.allCells,
                            unlocked_balls =it.unlocked_balls
                        )
                    )
                }
            }else{
                // if not logged create temporary user in local db, and delete this data when log in
                val  user = User("2137", "Name", 0,arrayListOf())
                user.let {
                    repository!!.addUserToRoom(
                        User(
                            id = it.id,
                            name = it.name,
                            points = it.points + maze.allCells,
                            unlocked_balls =it.unlocked_balls
                        )
                    )

                }

            }
        }
    }




    suspend fun getUser():State<User?>{
        val id = sharedPrefs!!.getString("uId", "")
        if (!id.isNullOrEmpty())
            withContext(Dispatchers.IO) {
                val user= repository!!.getUser(id)
                currentUser.value =user
                user?.let{currentUserPoints.value= it.points}
            }
        return currentUser
    }

    fun editCurrentUserName(userName:String){
        CoroutineScope(Dispatchers.IO).launch {
            val id= sharedPrefs?.getString("uId","")
            if (!id.isNullOrEmpty()) {
                val user = repository?.getUser(id)
                user?.let {
                    repository!!.addUser(
                        User(
                            id = it.id,
                            name = userName,
                            points = it.points,
                            unlocked_balls = it.unlocked_balls
                        )
                    )
                }
            }
            currentUserName.value=userName
        }
    }
    fun getUsersStats():State<List<Pair<Int,User>>?>{
        CoroutineScope(Dispatchers.IO).launch {
            sharedPrefs?.let {
                userStats.value=repository?.getFirst50AndUser(it.getString("uId","")!!)
            }
        }
        return userStats
    }


    /**
     * VERY IMPORTANT TO CALL IT BEFORE  setFirebaseRepository
     */
    fun setRepositoryAndSharedPreferences(application: Application){
        val usersDao=UsersDatabase.getDatabase(application).userDao()
        repository=LumbzeRepositoryImplementation(
            roomUserDao =usersDao
        )
        sharedPrefs = application.getSharedPreferences("sharedPreferences",
            ComponentActivity.MODE_PRIVATE
        )
        _rowsAmount.value=sharedPrefs!!.getInt("rows_amount",10)
        _cellSize.value=sharedPrefs!!.getInt("cell_size",40)
    }

    /**
     * NEED TO CALL SET REPOSITORY BEFORE!!!!
     */
    fun setFirebaseInRepository(usersFirebaseDAO: UsersFirebaseDAO){
        CoroutineScope(Dispatchers.IO).launch {
            repository!!.setFirebase(usersFirebaseDAO = usersFirebaseDAO)
        }
    }
    fun detachFirebaseListener(){
        CoroutineScope(Dispatchers.IO).launch {
            repository?.closeDatabases()
        }
    }


    /**
     * just visually
     */
    fun resetPositionAndRotation(){
        val rot = ValueAnimator.ofFloat( rotation.value,0f)
        rot.duration = 100 //in millis
        rot.addUpdateListener { animation ->
            MainScope().launch {
                setRotation(animation.animatedValue as Float)
            }
        }
        rot.start()

        val ballPos:Pair<Float,Float> = maze.getBallPosition()
        val va = ValueAnimator.ofFloat( translation.value,ballPos.first)
        va.duration = 100 //in millis
        va.addUpdateListener { animation ->
            MainScope().launch {
                setTranslation(animation.animatedValue as Float)
            }
        }
        va.start()
    }



}