package com.practicum.playlist_maker.pdfreader.tools.pdf.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.Scroller
import java.io.File
import kotlin.math.max
import kotlin.math.min

class CustomPdfView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var nextPage: PdfRenderer.Page? = null
    private var prevPage: PdfRenderer.Page? = null
    private var currentPageIndex: Int = 0
    private var pageCount: Int = 0

    private var currentBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null
    private var prevBitmap: Bitmap? = null

    private var totalScrollY = 0f
    private var pageHeight = 0f
    private val spacing = 0f // Расстояние между страницами
    private val scroller = Scroller(context)

    private val pageNumberPaint = Paint().apply {
        color = Color.BLACK
        alpha = 180
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val gestureDetector: GestureDetector
    private var onPageChangeListener: ((Int, Int) -> Unit)? = null
    private var onErrorListener: ((Throwable) -> Unit)? = null

    private val pageBorderPaint = Paint().apply {
        color = Color.GRAY
        alpha = 100
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val pageShadowPaint = Paint().apply {
        color = Color.GRAY
        alpha = 30
        style = Paint.Style.FILL
    }

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                handleScroll(distanceY)
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                scroller.fling(0, totalScrollY.toInt(), 0, -velocityY.toInt(),
                    0, 0, 0, ((pageCount - 1) * (pageHeight + spacing)).toInt())
                postInvalidateOnAnimation()
                return true
            }
        })
    }

    private fun handleScroll(distanceY: Float) {
        totalScrollY = (totalScrollY + distanceY).coerceIn(
            0f,
            (pageCount - 1) * (pageHeight + spacing)
        )

        val currentPageFloat = totalScrollY / (pageHeight + spacing)
        val newPageIndex = currentPageFloat.toInt()

        if (newPageIndex != currentPageIndex) {
            currentPageIndex = newPageIndex
            loadVisiblePages()
            onPageChangeListener?.invoke(currentPageIndex + 1, pageCount)
        }

        invalidate()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            totalScrollY = scroller.currY.toFloat()
            val currentPageFloat = totalScrollY / (pageHeight + spacing)
            val newPageIndex = currentPageFloat.toInt()

            if (newPageIndex != currentPageIndex) {
                currentPageIndex = newPageIndex
                loadVisiblePages()
                onPageChangeListener?.invoke(currentPageIndex + 1, pageCount)
            }

            invalidate()
            postInvalidateOnAnimation()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                scroller.abortAnimation()
            }
        }
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val scale = width.toFloat() / (currentBitmap?.width ?: 1)
        val scaledPageHeight = (currentBitmap?.height ?: 0) * scale
        pageHeight = scaledPageHeight

        val currentPageOffset = totalScrollY % (pageHeight + spacing)
        val baseY = -currentPageOffset

        // Отрисовка предыдущей страницы
        if (currentPageIndex > 0) {
            prevBitmap?.let { bitmap ->
                val pageTop = (baseY - pageHeight - spacing)

                // Тень страницы
                canvas.drawRect(
                    4f,
                    pageTop + 4f,
                    width.toFloat(),
                    pageTop + pageHeight + 4f,
                    pageShadowPaint
                )

                // Сама страница
                canvas.save()
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, pageTop / scale, null)
                canvas.restore()

                // Рамка страницы
                canvas.drawRect(
                    0f,
                    pageTop,
                    width.toFloat(),
                    pageTop + pageHeight,
                    pageBorderPaint
                )
            }
        }

        // Отрисовка текущей страницы
        currentBitmap?.let { bitmap ->
            // Тень страницы
            canvas.drawRect(
                4f,
                baseY + 4f,
                width.toFloat(),
                baseY + pageHeight + 4f,
                pageShadowPaint
            )

            // Сама страница
            canvas.save()
            canvas.scale(scale, scale)
            canvas.drawBitmap(bitmap, 0f, baseY / scale, null)
            canvas.restore()

            // Рамка страницы
            canvas.drawRect(
                0f,
                baseY,
                width.toFloat(),
                baseY + pageHeight,
                pageBorderPaint
            )
        }

        // Отрисовка следующей страницы
        if (currentPageIndex < pageCount - 1) {
            nextBitmap?.let { bitmap ->
                val pageTop = baseY + pageHeight + spacing

                // Тень страницы
                canvas.drawRect(
                    4f,
                    pageTop + 4f,
                    width.toFloat(),
                    pageTop + pageHeight + 4f,
                    pageShadowPaint
                )

                // Сама страница
                canvas.save()
                canvas.scale(scale, scale)
                canvas.drawBitmap(bitmap, 0f, pageTop / scale, null)
                canvas.restore()

                // Рамка страницы
                canvas.drawRect(
                    0f,
                    pageTop,
                    width.toFloat(),
                    pageTop + pageHeight,
                    pageBorderPaint
                )
            }
        }

        // Отображаем номер страницы
        if (pageCount > 0) {
            val pageText = "${currentPageIndex + 1}/$pageCount"
            canvas.drawText(pageText, width / 2f, height - 50f, pageNumberPaint)
        }
    }

    private fun loadVisiblePages() {
        try {
            // Загружаем предыдущую страницу
            if (currentPageIndex > 0) {
                prevPage?.close()
                prevPage = pdfRenderer?.openPage(currentPageIndex - 1)
                prevBitmap?.recycle()
                prevBitmap = createBitmapFromPage(prevPage)
            } else {
                prevPage?.close()
                prevBitmap?.recycle()
                prevPage = null
                prevBitmap = null
            }

            // Загружаем текущую страницу
            currentPage?.close()
            currentPage = pdfRenderer?.openPage(currentPageIndex)
            currentBitmap?.recycle()
            currentBitmap = createBitmapFromPage(currentPage)

            // Загружаем следующую страницу
            if (currentPageIndex < pageCount - 1) {
                nextPage?.close()
                nextPage = pdfRenderer?.openPage(currentPageIndex + 1)
                nextBitmap?.recycle()
                nextBitmap = createBitmapFromPage(nextPage)
            } else {
                nextPage?.close()
                nextBitmap?.recycle()
                nextPage = null
                nextBitmap = null
            }
        } catch (e: Exception) {
            Log.e("CustomPdfView", "Error loading visible pages", e)
            onErrorListener?.invoke(e)
        }
    }

    private fun createBitmapFromPage(page: PdfRenderer.Page?): Bitmap? {
        return page?.let {
            val bitmap = Bitmap.createBitmap(
                it.width,
                it.height,
                Bitmap.Config.ARGB_8888
            )
            it.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }
    }

    fun loadPdf(file: File) {
        try {
            closePdfRenderer()

            val fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            pageCount = pdfRenderer?.pageCount ?: 0

            if (pageCount > 0) {
                currentPageIndex = 0
                totalScrollY = 0f
                loadVisiblePages()
                onPageChangeListener?.invoke(currentPageIndex + 1, pageCount)
            }
        } catch (e: Exception) {
            Log.e("CustomPdfView", "Error loading PDF", e)
            onErrorListener?.invoke(e)
        }
    }

    fun setOnPageChangeListener(listener: (page: Int, total: Int) -> Unit) {
        onPageChangeListener = listener
    }

    fun setOnErrorListener(listener: (error: Throwable) -> Unit) {
        onErrorListener = listener
    }

    private fun closePdfRenderer() {
        try {
            prevPage?.close()
            currentPage?.close()
            nextPage?.close()
            prevBitmap?.recycle()
            currentBitmap?.recycle()
            nextBitmap?.recycle()
            pdfRenderer?.close()
        } catch (e: Exception) {
            Log.e("CustomPdfView", "Error closing PDF renderer", e)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        closePdfRenderer()
    }
} 