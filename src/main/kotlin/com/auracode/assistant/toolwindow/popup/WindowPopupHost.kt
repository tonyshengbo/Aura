package com.auracode.assistant.toolwindow.popup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import java.awt.AWTEvent
import java.awt.Component
import java.awt.GraphicsEnvironment
import java.awt.KeyboardFocusManager
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyBoundsAdapter
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

/** 仅负责 AWT 窗口、定位与事件生命周期；不依赖业务菜单或输入框的显隐状态。 */
@OptIn(ExperimentalComposeUiApi::class)
internal class WindowPopupHost(
    private val anchorPanel: JComponent,
    private val properties: PopupProperties,
    private val onDismissRequest: () -> Unit,
) {
    private val owner = requireNotNull(SwingUtilities.getWindowAncestor(anchorPanel))
    val window = ComposeDialog(owner).apply {
        name = "Aura Compose popup"
        isUndecorated = true
        isResizable = false
        type = Window.Type.POPUP
        focusableWindowState = properties.focusable
        isAutoRequestFocus = properties.focusable
        // 必须在创建 native peer 前启用官方透明链路，同时配置窗口与 Skia 画布。
        // 单独设置 AWT background 不会让 ComposePanel 的画布透明，阴影留白会变成黑/白框。
        isTransparent = true
        rootPane.isOpaque = false
        (contentPane as JComponent).isOpaque = false
        exceptionHandler = (anchorPanel as? ComposePanel)?.exceptionHandler
            ?: (owner as? ComposeDialog)?.exceptionHandler
    }
    val panel = window.contentPane.components.single() as JComponent
    var maxContentSize by mutableStateOf(IntSize(1, 1))
        private set
    var focused by mutableStateOf(false)
        private set
    private var anchorBounds = Rect.Zero
    private var anchorScale = 1f
    private var layoutDirection = LayoutDirection.Ltr
    private var positionProvider: PopupPositionProvider? = null
    private var contentSize = IntSize.Zero
    private var previousFocus: Component? = null
    private var disposed = false
    private var dismissing = false
    private var layoutScheduled = false

    private val ownerListener = object : WindowAdapter() {
        override fun windowClosed(e: WindowEvent) = dismissAndClose()
        override fun windowIconified(e: WindowEvent) = dismissAndClose()
        override fun windowLostFocus(e: WindowEvent) {
            if (!belongsTo(e.oppositeWindow, owner)) dismissAndClose()
        }
    }
    private val popupListener = object : WindowAdapter() {
        override fun windowGainedFocus(e: WindowEvent) {
            focused = true
        }

        override fun windowLostFocus(e: WindowEvent) {
            focused = false
            // 返回锚点窗口时由鼠标外点事件决定关闭，避免点击触发按钮发生 dismiss/toggle 重开。
            if (e.oppositeWindow !== owner && !belongsTo(e.oppositeWindow, window)) dismissAndClose()
        }
    }
    private val componentListener = object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) = scheduleLayout()
        override fun componentResized(e: ComponentEvent) = scheduleLayout()
        override fun componentHidden(e: ComponentEvent) = dismissAndClose()
    }
    private val ancestorListener = object : HierarchyBoundsAdapter() {
        override fun ancestorMoved(e: HierarchyEvent) = scheduleLayout()
        override fun ancestorResized(e: HierarchyEvent) = scheduleLayout()
    }
    private val hierarchyListener = HierarchyListener { event ->
        if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !anchorPanel.isShowing) {
            dismissAndClose()
        }
    }
    private val outsideMouseListener = AWTEventListener { event ->
        if (event is MouseEvent && event.id == MouseEvent.MOUSE_PRESSED && properties.dismissOnClickOutside) {
            val sourceWindow = SwingUtilities.getWindowAncestor(event.component)
            if (!belongsTo(sourceWindow, window)) {
                // 点击自身触发按钮由按钮执行 toggle，避免先 dismiss 再 toggle 反而重新打开。
                val anchor = screenAnchor()
                if (anchor == null || !anchor.contains(event.locationOnScreen)) dismissAndClose()
            }
        }
    }

    private fun installListeners() {
        owner.addWindowListener(ownerListener)
        owner.addWindowFocusListener(ownerListener)
        owner.addComponentListener(componentListener)
        window.addWindowFocusListener(popupListener)
        anchorPanel.addHierarchyBoundsListener(ancestorListener)
        anchorPanel.addHierarchyListener(hierarchyListener)
        Toolkit.getDefaultToolkit().addAWTEventListener(outsideMouseListener, AWTEvent.MOUSE_EVENT_MASK)
        if (properties.dismissOnBackPress) {
            // Swing 在焦点目标处理过按键后才查窗口绑定，保留原生输入法取消组合的优先权。
            window.rootPane.registerKeyboardAction(
                { dismissAndClose() }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW,
            )
        }
    }

    fun updateAnchor(bounds: Rect, scale: Float, direction: LayoutDirection, provider: PopupPositionProvider) {
        anchorBounds = bounds
        anchorScale = scale
        layoutDirection = direction
        positionProvider = provider
        scheduleLayout()
    }

    fun updateContentSize(size: IntSize) {
        if (contentSize == size) return
        contentSize = size
        scheduleLayout()
    }

    /** 先创建离屏 peer，使 Compose 能测量内容；测量完成后才真正显示窗口。 */
    fun mount() {
        if (disposed) return
        installListeners()
        previousFocus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
        updateWorkArea()
        window.pack()
        scheduleLayout()
    }

    fun dismiss() = dismissAndClose()

    private fun scheduleLayout() {
        if (disposed || layoutScheduled) return
        layoutScheduled = true
        SwingUtilities.invokeLater {
            layoutScheduled = false
            if (!disposed) updateBounds()
        }
    }

    private fun screenAnchor(): Rectangle? = if (anchorPanel.isShowing) {
        // 从 AWT 布局与窗口 bounds 计算，避免移动过程中 native peer 的屏幕位置暂未更新。
        val origin = SwingUtilities.convertPoint(anchorPanel, Point(0, 0), owner).apply { translate(owner.x, owner.y) }
        popupScreenAnchor(anchorBounds, origin, anchorScale)
    } else null

    private fun workArea(): Rectangle {
        val anchor = screenAnchor()
        val configuration = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
            .map { it.defaultConfiguration }
            .firstOrNull { anchor != null && it.bounds.contains(anchor.centerX, anchor.centerY) }
            ?: anchorPanel.graphicsConfiguration
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        return Rectangle(configuration.bounds).apply {
            x += insets.left
            y += insets.top
            width -= insets.left + insets.right
            height -= insets.top + insets.bottom
        }
    }

    private fun updateWorkArea(): Rectangle {
        val area = workArea()
        val scale = window.graphicsConfiguration.defaultTransform.scaleX.toFloat()
        maxContentSize = IntSize((area.width * scale).toInt(), (area.height * scale).toInt())
        return area
    }

    private fun updateBounds() {
        val anchor = screenAnchor() ?: return dismissAndClose()
        val provider = positionProvider ?: return
        val area = updateWorkArea()
        if (contentSize == IntSize.Zero) return
        val bounds = popupScreenBounds(
            anchor, area, contentSize, window.graphicsConfiguration.defaultTransform.scaleX.toFloat(),
            layoutDirection, PaddedPopupPositionProvider(provider, (PopupWindowPadding * anchorScale).roundToInt()),
            providerScale = anchorScale,
        )
        if (window.bounds != bounds) window.bounds = bounds
        if (!window.isVisible) {
            window.isVisible = true
            if (properties.focusable) panel.requestFocusInWindow()
        }
    }

    private fun dismissAndClose() {
        if (disposed || dismissing) return
        dismissing = true
        close()
        onDismissRequest()
    }

    fun close() {
        if (disposed) return
        disposed = true
        val restoreFocus = window.isFocused
        Toolkit.getDefaultToolkit().removeAWTEventListener(outsideMouseListener)
        owner.removeWindowListener(ownerListener)
        owner.removeWindowFocusListener(ownerListener)
        owner.removeComponentListener(componentListener)
        window.removeWindowFocusListener(popupListener)
        anchorPanel.removeHierarchyBoundsListener(ancestorListener)
        anchorPanel.removeHierarchyListener(hierarchyListener)
        // 原生子组件必须先 removeNotify，再释放 Compose，避免已释放编辑器重新挂载。
        window.contentPane.remove(panel)
        window.dispose()
        if (restoreFocus) SwingUtilities.invokeLater {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            if (owner.isShowing && (manager.focusedWindow == null || manager.focusedWindow === owner)) {
                previousFocus?.takeIf { it.isShowing }?.requestFocusInWindow()
            }
        }
    }
}

private fun belongsTo(window: Window?, ancestor: Window): Boolean =
    generateSequence(window) { it.owner }.any { it === ancestor }
