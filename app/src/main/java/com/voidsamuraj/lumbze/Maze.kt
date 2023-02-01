package com.voidsamuraj.lumbze

import android.animation.ValueAnimator
import android.content.res.Resources
import android.graphics.*
import android.view.animation.LinearInterpolator
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.animation.doOnEnd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.lang.Math.*
import java.util.*
import kotlin.math.roundToInt


class Maze(val doOnComplete:()->Unit){
    enum class CellDirections{
        OUT,RIGHT,CENTER,LEFT
    }
    private enum class CellState{
        UNVISITED,VISITED
    }

    private lateinit var mazeViewModel: MazeViewModel
    private var drawableWidth: Int=10
    private lateinit var bitmap:Bitmap
    private lateinit var cellSize:State<Int>
    private lateinit var updateCellSize:(Int)->Unit
    private var centerX:Float=0f
    private var centerY:Float=0f
    private var canvasEdit: Canvas?=null
    private var canvasSide:Int=0
    private  var currentRotation:Float=0f
    val  ended:MutableState<Boolean> = mutableStateOf(false)
    private var ballPos:Pair<Int,Int>  = Pair(-1,0)
    private lateinit var endpoint:Pair<Int,Int>
    private var _allCells:Int=0
    val allCells get() = _allCells
    @Suppress("UNCHECKED_CAST")
    /**
     * working path
     */
    private var pathToWin:Stack<Pair<Int,Int>> = Stack()
    private val _helpPathBitmap:MutableState<Bitmap> = mutableStateOf(Bitmap.createBitmap(drawableWidth, drawableWidth, Bitmap.Config.ARGB_8888))
    val helpPathBitmap:State<Bitmap> = _helpPathBitmap

    /**
     * current path of displayed help dots
     */
    private var helpPathStack:Stack<Pair<Int,Int>> = Stack()
    /**
     * cells outside working path
     */
    private val moveOutPath:Stack<Pair<Int,Int>> = Stack()


    private var paint: Paint=Paint().apply {
        color=Color.rgb(139,69,19)
        style= Paint.Style.STROKE
        strokeWidth=10f
    }
    private  var rows:Int=0
    private var cellsData:ArrayList<Pair<Double,Int>> = arrayListOf()
    /**
     * Triple(
     *       CellState,
     *       List of Walls,
     *       List of possible moves,
     *      )
     */
    private var mazeArray:ArrayList<ArrayList<Triple<CellState,List<CellDirections>,List<CellDirections>>>> = arrayListOf()

    //first need to increment
    @Suppress("UNCHECKED_CAST")
    fun getPathToWin():Stack<Pair<Int,Int>>  =pathToWin.clone() as Stack<Pair<Int,Int>>
    /**
     * @return first is height margin relative
     *          second is angle of rotation only current rotation
     *         !!!Clears Rotation !!!
     */
    fun getBallPosition():Pair<Float,Float> {
        val rotCp=currentRotation
        currentRotation=0f
        return if(ballPos.first!=-1) {
            Pair(
                (cellSize.value * (ballPos.first+1.5 )).toFloat(),
                rotCp
            )

        }else
            Pair(0f,0f)
    }
    /**
     * @return gets ball pos in cells
     */
    fun getBallPos():Pair<Int,Int> {
        return Pair(
            ballPos.first,
            ballPos.second
        )
    }
    fun postData(cellSize:State<Int>, canvasSideSize:Int, updateCellSize:(Int)->Unit, screenWidth:Int, mazeViewModel: MazeViewModel){
        this.updateCellSize=updateCellSize
        this.cellSize=cellSize
        this.canvasSide=canvasSideSize
        this.drawableWidth=screenWidth
        this.mazeViewModel=mazeViewModel
        // ended.value=false
    }

    fun getMaze():Bitmap {

        this.canvasSide.let { side->
            bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            canvasEdit = Canvas(bitmap)
            centerX = side / 2f
            centerY = side / 2f
            displayMaze()
            bitmap.prepareToDraw()
            return bitmap
        }
    }
    private fun getPathDrawable(stack: Stack<Pair<Int, Int>>):Bitmap {
        val pathBitmap = Bitmap.createBitmap(drawableWidth, drawableWidth, Bitmap.Config.ARGB_8888)
        val pathCanvas  = Canvas(pathBitmap)
        val pathCenter = Pair(drawableWidth / 2,drawableWidth / 2)

        drawPoints(pathCanvas,stack,pathCenter)
        pathBitmap.prepareToDraw()
        return pathBitmap
    }
    fun getBall(res:Resources):Bitmap{
        val mBitmap= BitmapFactory.decodeResource(res,mazeViewModel.getBallStyle())
        return Bitmap.createScaledBitmap(mBitmap, cellSize.value, cellSize.value, false)

           //     drawCircle(width/2f,height/2f,width/2f,Paint().apply {color=Color.RED  })
    }

    /**
     * moves and updates help path
     * */
    fun moveBall(direction:CellDirections){
        //add 1 because center is -1
        //last pos is updated every move
        var lastPos:Pair<Int,Int> = Pair(ballPos.first+1,ballPos.second)

        fun updateHelp(){
            // fun onEnd() {
            val ballPosInt = ballPos.copy()
            val ballPosIntInc = Pair(ballPos.first + 1, ballPos.second)
            val ballPos:Pair<Float,Float> = getBallPosition()
            if (getPathToWin().contains(ballPosInt)) {
                //clear if nothing to follow
                moveOutPath.clear()

                if (helpPathStack.contains(lastPos) && lastPos != ballPosIntInc)
                    helpPathStack.remove(lastPos)

                moveOutPath.add(Pair(ballPosIntInc.first, ballPosIntInc.second))
            }
            //add pos outside track (last item in also)
            else if (!moveOutPath.contains(ballPosIntInc)) {
                moveOutPath.add(ballPosIntInc)
            }
            //if balPos is second time in list, that means we are moving back
            else if (lastPos != ballPosIntInc) {
                moveOutPath.remove(lastPos)
                helpPathStack.remove(lastPos)
            }


            //if changed pos
            @Suppress("UNCHECKED_CAST")
            if(lastPos!=ballPosIntInc)
                _helpPathBitmap.value=mazeViewModel.maze.getPathDrawable(helpPathStack.clone() as Stack<Pair<Int,Int>>)
            lastPos=ballPosIntInc
            val lock = Object()

            //maze rotation and ball movement
            CoroutineScope(Dispatchers.Main).launch{
                if(direction==CellDirections.CENTER||direction==CellDirections.OUT){
                    val va = ValueAnimator.ofFloat( mazeViewModel.translation.value,ballPos.first)
                    va.duration = 80 //in millis
                    va.interpolator = LinearInterpolator()
                    va.addUpdateListener { animation ->
                        MainScope().launch {
                            mazeViewModel.setTranslation(animation.animatedValue as Float)
                        }
                    }
                    va.doOnEnd {

                        synchronized(lock) {
                            CoroutineScope(Dispatchers.Main).launch {
                                mazeViewModel.setIsScreenTouchable(true)
                            }
                            lock.notify()
                        }
                    }
                    va.start()
                    val vaRot = ValueAnimator.ofFloat( mazeViewModel.rotation.value,mazeViewModel.rotation.value+ ballPos.second)
                    vaRot.duration = 80 //in millis
                    va.interpolator = LinearInterpolator()
                    vaRot.addUpdateListener { animation ->
                        MainScope().launch {
                            mazeViewModel.setRotation( animation.animatedValue as Float)
                        }
                    }
                    vaRot.start()

                }   else{
                    val va = ValueAnimator.ofFloat( mazeViewModel.rotation.value,mazeViewModel.rotation.value+ ballPos.second)
                    va.duration = 80 //in millis
                    va.interpolator = LinearInterpolator()
                    va.addUpdateListener { animation ->
                        MainScope().launch {
                            mazeViewModel.setRotation(animation.animatedValue as Float)
                        }
                    }
                    va.doOnEnd {
                        synchronized(lock) {
                            CoroutineScope(Dispatchers.Main).launch {
                                mazeViewModel.setIsScreenTouchable(true)
                            }
                            lock.notify()
                        }
                    }
                    va.start()

                }
            }

            synchronized(lock) {
                lock.wait()
            }
            Pair(0,1).let{
                if(moveOutPath.contains(it))
                    moveOutPath.remove(it)
            }

        }

        if(!ended.value){
            when(direction) {
                CellDirections.CENTER -> {
                    do {

                        if (ballPos.first > 0 && (!mazeArray[ballPos.first][ballPos.second].second.contains(
                                CellDirections.CENTER
                            ))
                        ) {

                            //if amount of cells change in next row
                            if (cellsData[ballPos.first].second == cellsData[ballPos.first - 1].second * 2) {
                                val rot = (180f / cellsData[ballPos.first].second)

                                if (ballPos.second != 0
                                ) {

                                    if (ballPos.second % 2 == 1) {
                                        currentRotation += rot

                                    } else {
                                        currentRotation -= rot
                                    }
                                    ballPos = Pair(ballPos.first - 1, ballPos.second / 2)

                                } else {
                                    ballPos = Pair(ballPos.first - 1, 0)
                                }
                            } else {

                                ballPos = Pair(ballPos.first - 1, ballPos.second)
                            }

                            //if can move to center, (0,1) can always move to center
                        } else if (ballPos.second == 1) {
                            ballPos = Pair(-1, 0)
                        }

                        updateHelp()

                    }while(
                        (ballPos.first==0&&ballPos.second==1)||
                        (ballPos.first > 0 &&
                                ((!mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.CENTER))&&
                                        mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.LEFT)&&
                                        mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.RIGHT)
                                        )
                                )
                    )
                }
                CellDirections.OUT -> {
                    do{
                        if (ballPos.first==-1){
                            ballPos=Pair(0,1)
                        }
                        else if (ballPos.first != rows - 1 && (!mazeArray[ballPos.first][ballPos.second].second.contains(
                                CellDirections.OUT
                            ))
                        ) {
                            ballPos = Pair(ballPos.first + 1, ballPos.second)

                            //if amount of cells change in next row
                            if (cellsData[ballPos.first].second == 2 * cellsData[ballPos.first - 1].second) {
                                val rot = (180f / cellsData[ballPos.first].second)
                                if (mazeArray[ballPos.first][ballPos.second * 2].second.contains(
                                        CellDirections.CENTER
                                    )
                                ) {
                                    currentRotation -= rot
                                    ballPos = Pair(ballPos.first, ballPos.second*2+1)
                                }else {
                                    currentRotation += rot
                                    ballPos = Pair(ballPos.first, ballPos.second*2)
                                }

                            }
                            //maze end
                        }else if(ballPos.first==endpoint.first&&ballPos.second==endpoint.second){
                            ballPos = Pair(ballPos.first + 1, ballPos.second)
                            ended.value=true
                            helpPathStack.clear()
                            @Suppress("UNCHECKED_CAST")
                            _helpPathBitmap.value=mazeViewModel.maze.getPathDrawable(helpPathStack.clone() as Stack<Pair<Int,Int>>)
                            doOnComplete()
                        }

                        updateHelp()
                    }while(
                        (ballPos.first==endpoint.first&&ballPos.second==endpoint.second)||
                        ((ballPos.first < rows - 1 )&&
                                (!mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.OUT))&&
                                mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.LEFT)&&
                                mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.RIGHT)
                                )

                    )
                }
                CellDirections.LEFT -> {
                    if(ballPos.first!=-1)
                        do {
                            if ((!mazeArray[ballPos.first][ballPos.second].second.contains(
                                    CellDirections.LEFT
                                ))
                            ) {
                                var move = ballPos.second
                                if (move == 0) {
                                    move = cellsData[ballPos.first].second - 1

                                } else
                                    move -= 1
                                currentRotation += (360f / cellsData[ballPos.first].second)

                                ballPos = Pair(ballPos.first, move)
                            }
                            updateHelp()
                        }while (
                            (!mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.LEFT))&&
                            mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.CENTER)&&
                            mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.OUT)
                        )
                    else
                        CoroutineScope(Dispatchers.Main).launch {
                            mazeViewModel.setIsScreenTouchable(true)
                        }
                }
                CellDirections.RIGHT -> {
                    if(ballPos.first!=-1)
                        do{
                            if ((!mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.RIGHT))) {

                                var move = ballPos.second
                                if (move == cellsData[ballPos.first].second - 1) {
                                    move = 0
                                } else
                                    move += 1
                                currentRotation -= (360f / cellsData[ballPos.first].second)

                                ballPos = Pair(ballPos.first, move)
                            }
                            updateHelp()

                        }while (
                            (!mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.RIGHT))&&
                            mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.CENTER)&&
                            mazeArray[ballPos.first][ballPos.second].second.contains(CellDirections.OUT)
                        )
                    else
                        CoroutineScope(Dispatchers.Main).launch {
                            mazeViewModel.setIsScreenTouchable(true)
                        }
                }
            }
        }

    }

    fun calculateWidthAndAmount(row:Int):Pair<Double,Int>{
        var ret:Double=cellSize.value.toDouble()
        val smallLength=2*PI*cellSize.value
        var cellsAmount:Int=(smallLength/ret).toInt()
        ret=smallLength/cellsAmount
        for (i in 1 until row){

            val bigRadius=cellSize.value*(i+1)
            val bigLength=2*PI*bigRadius
            val splitedCells=bigLength/cellsAmount
            ret=splitedCells
            if(splitedCells/2>=cellSize.value)
                ret/=2
            cellsAmount=(bigLength/ret).toInt()
        }
        return Pair(ret,cellsAmount)
    }

    fun createMaze(rowsAmount:Int){

        pathToWin.clear()
        helpPathStack.clear()
        moveOutPath.clear()

        ballPos=Pair(-1,0)
        currentRotation=0f
        rows=rowsAmount
        if((rows+2)*cellSize.value*2>canvasSide){
            updateCellSize(canvasSide/((2*(rows+2))+2))
        }
        cellsData= arrayListOf()
        mazeArray= arrayListOf()
        var currentRow=0
        var currentColumn: Int
        val stos:Stack<Pair<Int,Int>> = Stack()
        var maxStack=0
        fun getCellState(row:Int,col:Int):CellState=mazeArray[row][col].first
        fun removeCellWall(row: Int,column: Int,wall:CellDirections){
            mazeArray[row].apply{
                set(column,
                    Triple(
                        CellState.VISITED,
                        get(column).second.toMutableList()
                            .apply {  remove(wall)}
                            .toList(),
                        get(column).third
                    )
                )
            }
        }

        fun changeCellState(row: Int,column: Int,state:CellState){
            mazeArray[row].apply{
                set(column,
                    Triple(
                        state,
                        get(column).second,
                        get(column).third
                    )
                )
            }
        }

        fun removeMovement(row: Int,column: Int,direction:CellDirections){
            mazeArray[row].apply{
                set(column,
                    Triple(
                        get(column).first,
                        get(column).second,
                        get(column).third.toMutableList()
                            .apply {  remove(direction)}
                            .toList()
                    )
                )
            }

        }

        //calculate number of cells, cells in row and widths
        val fullList=listOf(CellDirections.OUT,CellDirections.CENTER,CellDirections.LEFT,CellDirections.RIGHT)
        val outList=listOf(CellDirections.CENTER,CellDirections.LEFT,CellDirections.RIGHT)
        val centerList=listOf(CellDirections.OUT,CellDirections.LEFT,CellDirections.RIGHT)
        _allCells=0
        for (i in 1..rows){
            val data=calculateWidthAndAmount(i)
            cellsData.add(data)
            _allCells+=data.second
            mazeArray.add(
                ArrayList(
                    (1..data.second).map {
                        when(i){
                            1-> Triple(CellState.UNVISITED,fullList, centerList)
                            rows->Triple(CellState.UNVISITED,fullList, outList)
                            else-> Triple(CellState.UNVISITED,fullList, fullList)
                        }
                    }
                )
            )
        }


        //first column
        currentColumn=1
        removeCellWall(0,currentColumn,CellDirections.CENTER)
        changeCellState(0,currentColumn,CellState.VISITED)
        removeMovement(0,currentColumn,CellDirections.CENTER)

        //change cells in maze
        do{
            lateinit var dir:CellDirections
            val options:Int = mazeArray[currentRow][currentColumn].third.size
            if (options!=0){

                dir= mazeArray[currentRow][currentColumn].third[(random()*options).toInt()]
                if(dir==CellDirections.LEFT){
                    var nextPos=currentColumn-1
                    if(nextPos<0)
                        nextPos=cellsData[currentRow].second-1
                    if(getCellState(currentRow,nextPos)==CellState.UNVISITED){

                        removeCellWall(currentRow,currentColumn,CellDirections.LEFT)
                        removeCellWall(currentRow,nextPos,CellDirections.RIGHT)
                        removeMovement(currentRow,nextPos,CellDirections.RIGHT)
                        removeMovement(currentRow,currentColumn,CellDirections.LEFT)

                        changeCellState(currentRow,nextPos,CellState.VISITED)
                        stos.add(Pair(currentRow,currentColumn))
                        if(stos.size>maxStack) {
                            @Suppress("UNCHECKED_CAST")
                            pathToWin = stos.clone() as Stack<Pair<Int, Int>>
                            maxStack=stos.size
                        }
                        currentColumn=nextPos

                    }else
                        removeMovement(currentRow,currentColumn,CellDirections.LEFT)

                }else if(dir==CellDirections.RIGHT){
                    var nextPos=currentColumn+1
                    if(nextPos>=cellsData[currentRow].second)
                        nextPos=0

                    if(getCellState(currentRow,nextPos)==CellState.UNVISITED){

                        removeCellWall(currentRow,currentColumn,CellDirections.RIGHT)
                        removeCellWall(currentRow,nextPos,CellDirections.LEFT)

                        removeMovement(currentRow,nextPos,CellDirections.LEFT)
                        removeMovement(currentRow,currentColumn,CellDirections.RIGHT)

                        changeCellState(currentRow,nextPos,CellState.VISITED)
                        stos.add(Pair(currentRow,currentColumn))
                        if(stos.size>maxStack) {
                            @Suppress("UNCHECKED_CAST")
                            pathToWin = stos.clone() as Stack<Pair<Int, Int>>
                            maxStack=stos.size
                        }
                        currentColumn=nextPos

                    }else
                        removeMovement(currentRow,currentColumn,CellDirections.RIGHT)

                }else if(dir==CellDirections.CENTER&&currentRow!=0){

                    val nextRow=currentRow-1
                    var nextColumn=currentColumn
                    val currentAmount:Int=cellsData[currentRow].second
                    val nextAmount:Int=cellsData[nextRow].second

                    if(nextAmount!=currentAmount)
                        nextColumn= kotlin.math.floor(currentColumn.toDouble() / 2).toInt()
                    if(getCellState(nextRow,nextColumn)==CellState.UNVISITED){

                        removeCellWall(currentRow,currentColumn,CellDirections.CENTER)
                        removeCellWall(nextRow,nextColumn,CellDirections.OUT)
                        removeMovement(nextRow,nextColumn,CellDirections.OUT)
                        removeMovement(currentRow,currentColumn,CellDirections.CENTER)

                        changeCellState(nextRow,nextColumn,CellState.VISITED)
                        stos.add(Pair(currentRow,currentColumn))
                        if(stos.size>maxStack) {
                            @Suppress("UNCHECKED_CAST")
                            pathToWin = stos.clone() as Stack<Pair<Int, Int>>
                            maxStack=stos.size
                        }
                        currentRow=nextRow
                        currentColumn=nextColumn
                    }else
                        removeMovement(currentRow,currentColumn,CellDirections.CENTER)

                }else if(dir==CellDirections.OUT&&currentRow!=rows-1){
                    val nextRow=currentRow+1
                    var nextColumn=currentColumn
                    val currentAmount:Int=cellsData[currentRow].second
                    val nextAmount:Int=cellsData[nextRow].second
                    if(nextAmount==2*currentAmount) {
                        nextColumn = currentColumn * 2 + random().roundToInt()
                    }
                    if(nextColumn<0)
                        nextColumn=0
                    if(getCellState(nextRow,nextColumn)==CellState.UNVISITED){

                        removeCellWall(currentRow,currentColumn,CellDirections.OUT)
                        removeCellWall(nextRow,nextColumn,CellDirections.CENTER)
                        removeMovement(nextRow,nextColumn,CellDirections.CENTER)
                        removeMovement(currentRow,currentColumn,CellDirections.OUT)

                        changeCellState(nextRow,nextColumn,CellState.VISITED)
                        stos.add(Pair(currentRow,currentColumn))
                        if(stos.size>maxStack) {
                            @Suppress("UNCHECKED_CAST")
                            pathToWin = stos.clone() as Stack<Pair<Int, Int>>
                            maxStack=stos.size
                        }
                        currentRow=nextRow
                        currentColumn=nextColumn

                    }else
                        removeMovement(currentRow,currentColumn,CellDirections.OUT)


                } else if(dir==CellDirections.OUT&&currentRow==rows-1){
                    removeMovement(currentRow,currentColumn,CellDirections.OUT)

                }else if(mazeArray[currentRow][currentColumn].third.isEmpty()){
                    //turn back
                    if(stos.isNotEmpty()){
                        val last=stos.pop()
                        currentRow=last.first
                        currentColumn=last.second
                    }
                }
            }else if(stos.isNotEmpty()){
                val last=stos.pop()
                changeCellState(currentRow,currentColumn,CellState.VISITED)
                currentRow=last.first
                currentColumn=last.second
                if(currentRow==0&&currentColumn==1)
                    break
            }
            else break

        } while(stos.isNotEmpty())
        @Suppress("UNCHECKED_CAST")
        val stackPos:Stack<Pair<Int,Int>> = pathToWin.clone() as Stack<Pair<Int,Int>>

        while (true){
            val pos:Pair<Int,Int> = stackPos.pop()
            if(pos.first==rows-1){
                endpoint=Pair(pos.first,pos.second)
                removeCellWall(pos.first,pos.second,CellDirections.OUT)
                break
            }
        }

    }

    private fun displayMaze(){

        val trunkRadius=(rows+2)*cellSize.value.toFloat()
        val trunkPaint=Paint().apply {

            color=Color.rgb(187, 134, 36)
            style=Paint.Style.FILL_AND_STROKE

        }
        canvasEdit?.drawCircle(centerX,centerY,trunkRadius,trunkPaint)
        for (i in 1..rows){
            for (j in 1..cellsData[i-1].second){
                drawCell(i,j,
                    mazeArray[i-1][j-1].second,
                    cellsData[i-1].first
                )
            }
        }
    }
    fun drawPoints(canvas:Canvas,stack: Stack<Pair<Int,Int>>,center:Pair<Int,Int>){
        val maxSize=14
        var size=maxSize//width/2
        while (stack.isNotEmpty()&&size>0){
            val point=stack.pop()
            drawPoint(canvas,point.first,point.second,center,size.toFloat()/maxSize)
            --size
        }
    }
    fun drawPoint(canvas:Canvas,row:Int,column:Int,center:Pair<Int,Int>,size:Float){

        val allRows=calculateWidthAndAmount(row).second
        val myAngle=toRadians((column.toDouble()+0.5f)/allRows*360)
        val oX1=(row+0.5f)*cellSize.value
        val myCos=cos(myAngle).toFloat()
        val mySin=sin(myAngle).toFloat()
        val x1=oX1*myCos+center.first
        val y1=oX1*mySin+center.second
        val paint=Paint().apply {
            color= androidx.compose.ui.graphics.Color.Green.hashCode()
            style=Paint.Style.FILL_AND_STROKE
        }
        canvas.drawCircle(x1,y1,cellSize.value*size/3f,paint)
    }
    private fun drawCell(row:Int, column:Int, directions:List<CellDirections>, width:Double){

        fun drawArc(radius:Int,startAngle:Float,endAngle:Float){
            canvasEdit?.drawArc(centerX-radius,centerY-radius,centerX+radius,centerY+radius,startAngle,endAngle-startAngle,false,paint)
        }
        fun drawLine(angle:Float){
            val myAngle=toRadians(angle.toDouble())
            val additional: Float =paint.strokeWidth/2
            val oX1=row*cellSize.value-additional
            val oX2=oX1+cellSize.value+additional*2
            val myCos=cos(myAngle).toFloat()
            val mySin=sin(myAngle).toFloat()
            val x1=oX1*myCos+centerX
            val x2=oX2*myCos+centerX
            val y1=oX1*mySin+centerY
            val y2=oX2*mySin+centerY
            canvasEdit?.drawLine(x1,y1,x2,y2,paint)
        }

        val smallRadius=cellSize.value*row
        val circleLength=2*PI*smallRadius
        val cellsAmount:Float=(circleLength/width).toFloat()

        if(column<=cellsAmount){
            val cellAngle=360f/cellsAmount
            val endAngle=cellAngle*column
            val startAngle=endAngle-cellAngle
            for(direction in directions){
                when(direction){
                    CellDirections.CENTER->
                        drawArc(smallRadius,startAngle,endAngle)
                    CellDirections.OUT->
                        drawArc(smallRadius+cellSize.value,startAngle,endAngle)
                    CellDirections.LEFT->
                        drawLine(startAngle)

                    CellDirections.RIGHT->
                        drawLine(endAngle)
                }
            }
        }
    }

    fun updateHelpDrawable(){

        val items=7
        val pathToWin:Stack<Pair<Int,Int>> = Stack()
        val movement:Stack<Pair<Int,Int>> = Stack()
        getPathToWin().forEach {
            movement.add(Pair(it.first+1,it.second))
        }
        @Suppress("UNCHECKED_CAST")
        val movement2:Stack<Pair<Int,Int>> = movement.clone() as Stack<Pair<Int,Int>>

        val movementReduced: Stack<Pair<Int, Int>> = Stack()

        var ballPosL: Pair<Int, Int>?

        getBallPos().let {
            ballPosL=Pair(it.first+1,it.second)
        }
        var itemInPath: Pair<Int, Int>?

        do{
            itemInPath = movement.pop()
            movementReduced.push(itemInPath)
        }while (
            movement.isNotEmpty()&&(itemInPath!=ballPosL)
        )

        var lastPos: Pair<Int, Int>?
        @Suppress("UNCHECKED_CAST")
        val mopc= moveOutPath.clone() as Stack<Pair<Int,Int>>

        for (i in 0..items)
        {
            if(mopc.size>1){
                lastPos=  mopc.pop()
                if(mopc.size==1){

                    //lastPos
                    val aim=  mopc.pop()
                    movementReduced.clear()
                    do{
                        itemInPath = movement2.pop()
                        movementReduced.push(itemInPath)
                    }while (
                        movement2.isNotEmpty()&&(itemInPath!=aim)
                    )

                }
                pathToWin.add(lastPos)
            }
            else if(movementReduced.isNotEmpty())
                pathToWin.add(movementReduced.pop())
        }
        helpPathStack=pathToWin.apply { reverse() }
        @Suppress("UNCHECKED_CAST")
        _helpPathBitmap.value=getPathDrawable(helpPathStack.clone() as Stack<Pair<Int,Int>>)

    }

}