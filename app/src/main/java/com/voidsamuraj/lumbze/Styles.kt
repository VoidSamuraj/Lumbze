package com.voidsamuraj.lumbze

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.runBlocking

@Composable
fun DisplayBalls(mazeViewModel: MazeViewModel,navigateBack:()->Unit){
    val idList=MazeViewModel.Companion.BallStyle.values()
    Image(painter = painterResource(id = R.drawable.log_cut), contentDescription = "BG", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
    LazyVerticalGrid(columns = GridCells.Fixed(2)){
        items(idList){
            DisplayItem(ball = it,mazeViewModel=mazeViewModel,navigateBack=navigateBack)
        }

    }
}
@Composable
fun DisplayItem(ball:MazeViewModel.Companion.BallStyle,mazeViewModel: MazeViewModel,navigateBack:()->Unit){
    Box(modifier = Modifier
        .fillMaxWidth()
        .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        val ballId = ball.resId
        val isPressed =remember{mutableStateOf(false)}
        val anim=animateFloatAsState(targetValue =  if(isPressed.value)  1.8f else 1f , animationSpec =spring(stiffness = 900f), finishedListener = {isPressed.value=false} )
        var ifContainAndId: Pair<Boolean, Int>?

        runBlocking {
            ifContainAndId= MazeViewModel.getIfContainAndMessageIdForBall(ball, mazeViewModel)
        }

        Image(painter = painterResource(id = R.drawable.block),
            contentDescription = "BALL",
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    onClick = {
                        if (ballId != MazeViewModel.Companion.BallStyle.NORMAL.resId && ifContainAndId?.first != true) {
                            isPressed.value = true

                        } else {
                            mazeViewModel.setBallStyle(ball)
                            mazeViewModel.setDrawerOpen(false)
                            navigateBack()
                        }

                    }
                ))

        Image(
            painter = painterResource(id = ballId),
            contentDescription = "BALL",
            modifier = Modifier.fillMaxSize(0.6f)
        )
        if (ballId != MazeViewModel.Companion.BallStyle.NORMAL.resId && ifContainAndId?.first != true){

            Box(contentAlignment = Alignment.Center, modifier = Modifier
                .fillMaxSize()
                .scale(anim.value)
            ) {


                Image(
                    painter = painterResource(id = R.drawable.lock),
                    contentDescription = "LOCKED",
                    modifier = Modifier.fillMaxSize(0.3f)
                )

                Text(
                    text = stringResource(id =ifContainAndId!!.second)

                )

            }
        }

    }
}
@Preview
@Composable
fun ShowWhatYouGot(){
    // DisplayBalls()
}