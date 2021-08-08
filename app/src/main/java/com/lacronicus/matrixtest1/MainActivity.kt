package com.lacronicus.matrixtest1

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.lacronicus.changenotifier.ChangeNotifierViewModel
import com.lacronicus.changenotifier.withChangeNotifier
import com.lacronicus.matrixtest1.ui.theme.MatrixTest1Theme
import java.math.BigDecimal
import java.math.MathContext

fun Float.round() = BigDecimal.valueOf(this.toDouble()).round(MathContext(3))

fun Offset.toLongString() = "Offset(${x.round()}, ${y.round()})"


class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalUnitApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatrixTest1Theme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    Screen("Android")
                }
            }
        }
    }
}

class MatrixViewModel : ChangeNotifierViewModel() {
    var painting = false
    val paint = Paint().apply {
        color = android.graphics.Color.BLUE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
    }


    var containerSizeX = 1f
    var containerSizeY = 1f

    var x = 0.5f
    var y = 0.5f
    var scale = 1f
    var rotation = 0f
    var rotationX = 0f
    var rotationY = 0f

    var tap = Offset(0f, 0f)

    var paintLayer: Bitmap
    var canvas: Canvas

    init {
        paintLayer = Bitmap.createBitmap(1080, 608, Bitmap.Config.ARGB_8888)
        canvas = Canvas(paintLayer)
        canvas.drawColor(0x77FF0000)
        canvas.drawPoint(10f, 10f, paint)
        canvas.drawCircle(10f, 10f, 10f, paint)

    }

    fun mutate(block: MatrixViewModel.() -> Unit) {
        block()
        constrain()
        notifyListeners()
        Log.d("TAG", "mutated, tap is now ${tap.toLongString()}")
    }

    fun constrain() {
        x = maxOf(x, -containerSizeX / 2.0f)
        y = maxOf(y, -containerSizeY / 2.0f)
        x = minOf(x, containerSizeX / 2.0f)
        y = minOf(y, containerSizeY / 2.0f)
        scale = maxOf(scale, 0.5f)
        scale = minOf(scale, 2.0f)
    }
}

@ExperimentalUnitApi
@Composable
fun Screen(name: String) {
    Column {
        val vm: MatrixViewModel = withChangeNotifier()

        val boxToNormalized = Matrix().apply {
            translate(-0.5f, -0.5f)
            scale(1 / vm.containerSizeX, 1 / vm.containerSizeY)
        }
        val imageTransform = Matrix().apply {
            translate(vm.x / vm.containerSizeX, vm.y / vm.containerSizeY)
            scale(vm.scale, vm.scale)
            invert()
        }

        val normalImageSpaceToBoxSpace = Matrix().apply {
            translate(vm.containerSizeX / 2, vm.containerSizeY / 2)
            translate(vm.x, vm.y)
            scale(vm.scale, vm.scale)
            rotateZ(vm.rotation)
            scale(vm.containerSizeX, vm.containerSizeY)
        }

        val boxSpaceToImageSpace = Matrix().apply {
            timesAssign(normalImageSpaceToBoxSpace)
            invert()
        }
        val corner1 = normalImageSpaceToBoxSpace.map(Offset(-0.5f, -0.5f))
        val corner2 = normalImageSpaceToBoxSpace.map(Offset(0.5f, -0.5f))
        val corner3 = normalImageSpaceToBoxSpace.map(Offset(-0.5f, 0.5f))
        val corner4 = normalImageSpaceToBoxSpace.map(Offset(0.5f, 0.5f))


        val normalizedImageSpaceTap = boxSpaceToImageSpace.map(vm.tap)
        Log.d("TAG", "normalized image space tap ${vm.tap.toLongString()} - ${normalizedImageSpaceTap.toLongString()}")

        Text(text = "Hello $name!")
        Column(modifier = Modifier.padding(10.dp)) {
            Text("image x, y: ${vm.x}, ${vm.y}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Text("rot | scale${vm.rotation} | ${vm.scale}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Text("container size: ${vm.containerSizeX}, ${vm.containerSizeY}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Text("tap: ${vm.tap}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Text("tap in normalized box space: ${boxToNormalized.map(vm.tap).toLongString()}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Text("tap in normalized image space: ${normalizedImageSpaceTap.toLongString()}", fontSize = TextUnit(10f, TextUnitType.Sp))
            Button(onClick = { vm.mutate { painting = !painting } }) {
                if (vm.painting) {
                    Text(text = "Currently Painting")
                } else {
                    Text(text = "Currently Moving")
                }
            }

        }


        Box(modifier = Modifier
            .pointerInput(Unit) {
                detectTapGestures {
                    vm.mutate {
                        tap = it
                    }
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->

                    val normalImageSpaceToBoxSpace = Matrix().apply {
                        translate(vm.containerSizeX / 2, vm.containerSizeY / 2)
                        translate(vm.x, vm.y)
                        scale(vm.scale, vm.scale)
                        rotateZ(vm.rotation)
                        scale(vm.containerSizeX, vm.containerSizeY)
                    }

                    val boxSpaceToImageNormalSpace = Matrix().apply {
                        timesAssign(normalImageSpaceToBoxSpace)
                        invert()
                    }
                    change.consumeAllChanges()
                    change.previousPressed
                    vm.mutate {
                        if (painting) {
                            tap = change.position
                            Log.d("TAG", "Set tap to ${tap.toLongString()}")
                            val normalSpaceTap = boxSpaceToImageNormalSpace.map(tap)
                            val imageNormalToImageSpace = Matrix().apply {
                                scale(vm.containerSizeX, vm.containerSizeY)
                                translate(0.5f, 0.5f)
                            }
                            val imageSpaceTap = imageNormalToImageSpace.map(normalSpaceTap)
                            Log.d("TAG", "tap at  ${tap}")
                            Log.d("TAG", "just received pointer ${tap.toLongString()} - ${normalSpaceTap.toLongString()}")
                            val last = imageNormalToImageSpace.map(boxSpaceToImageNormalSpace.map(change.previousPosition))
                            if (change.previousPressed) {
                                canvas.drawLine(last.x, last.y, imageSpaceTap.x, imageSpaceTap.y, paint)
                            } else {
                                canvas.drawCircle(imageSpaceTap.x, imageSpaceTap.y, 20f, paint)
                            }
                        } else {
                            x += dragAmount.x
                            y += dragAmount.y
                        }

                    }
                }
            }
            .background(Color.DarkGray)
            .onGloballyPositioned {

                if (vm.containerSizeX != it.size.width.toFloat() || vm.containerSizeY != it.size.height.toFloat())
                    vm.mutate {
                        vm.containerSizeX = it.size.width.toFloat()
                        vm.containerSizeY = it.size.height.toFloat()
                    }
            }
            .clipToBounds()
            .transformable(state = TransformableState { zoomChange, panChange, rotationChange ->
                if (!vm.painting) {
                    vm.mutate {
                        x += panChange.x
                        y += panChange.y
                        scale *= zoomChange
                        rotation += rotationChange
                    }
                }
            })) {

            Image(
                painter = painterResource(id = R.drawable.image),
                contentDescription = "an image",
                modifier = Modifier.graphicsLayer {
                    translationX = vm.x
                    translationY = vm.y
                    scaleX = vm.scale
                    scaleY = vm.scale
                    rotationZ = vm.rotation
                    rotationX = vm.rotationX
                    rotationY = vm.rotationY
                })
            Image(painter = BitmapPainter(vm.paintLayer.asImageBitmap()), contentDescription = "foo", modifier = Modifier.graphicsLayer {
                translationX = vm.x + vm.containerSizeX / 2 - vm.paintLayer.width / 2
                translationY = vm.y + vm.containerSizeY / 2 - vm.paintLayer.height / 2
                scaleX = vm.scale / vm.paintLayer.width * vm.containerSizeX
                scaleY = vm.scale / vm.paintLayer.height * vm.containerSizeY
                rotationZ = vm.rotation
                rotationX = vm.rotationX
                rotationY = vm.rotationY
            })

            Canvas(modifier = Modifier.background(Color(0x33FF0000)), onDraw = {
                drawPoint(vm.tap)
                drawPoint(corner1)
                drawPoint(corner2)
                drawPoint(corner3)
                drawPoint(corner4)

            })

        }

    }
}

fun DrawScope.drawPoint(offset: Offset) {
    drawCircle(color = Color.White, radius = 15f, center = offset, style = androidx.compose.ui.graphics.drawscope.Fill)
    drawCircle(color = Color.Black, radius = 15f, center = offset, style = Stroke(width = 8f))
}