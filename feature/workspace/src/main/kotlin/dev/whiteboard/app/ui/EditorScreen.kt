package dev.whiteboard.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.whiteboard.app.workspace.*
import dev.whiteboard.canvas.AssetProvider
import dev.whiteboard.canvas.CanvasAccessibilitySnapshot
import dev.whiteboard.canvas.WhiteboardView
import dev.whiteboard.core.model.*
import dev.whiteboard.ui.*
import dev.whiteboard.ui.R

@Composable
fun EditorScreen(
    editor: EditorUi,
    fingerDrawing: Boolean,
    snap: Boolean,
    brushes: List<BrushPreset>,
    favoriteColors: List<Int>,
    exporting: Boolean,
    viewModel: WorkspaceViewModel,
    assets: AssetProvider,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onImage: () -> Unit,
    onCamera: () -> Unit,
    onPdf: () -> Unit,
    onJpegExport: () -> Unit,
    onPdfExport: () -> Unit,
    onSettings: () -> Unit
) {
    val compact = LocalConfiguration.current.screenHeightDp < 500
    var nativeView by remember { mutableStateOf<WhiteboardView?>(null) }
    var more by remember { mutableStateOf(false) }
    var shapes by remember { mutableStateOf(false) }
    var pages by rememberSaveable { mutableStateOf(false) }
    var background by rememberSaveable { mutableStateOf(false) }
    var crop by rememberSaveable { mutableStateOf(false) }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var properties by rememberSaveable { mutableStateOf(false) }
    var objects by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !editor.saving) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back))
            }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(editor.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(
                        if (editor.saving) {
                            R.string.saving
                        } else if (editor.dirty) {
                            R.string.unsaved
                        } else {
                            R.string.saved_locally
                        }
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                viewModel.undo()
            }, enabled = editor.canUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
            IconButton(onClick = {
                viewModel.redo()
            }, enabled = editor.canRedo) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
            FilledTonalIconButton(onClick = {
                viewModel.save()
            }, enabled = !editor.saving) { Icon(Icons.Outlined.Save, stringResource(R.string.save)) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more)) }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.objects)) }, onClick = {
                        more = false
                        objects = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.select_all)) }, onClick = {
                        more = false
                        viewModel.selectAll()
                    }, enabled = editor.page.elements.isNotEmpty())
                    DropdownMenuItem(text = { Text(stringResource(R.string.paste)) }, onClick = {
                        more = false
                        viewModel.paste()
                    }, enabled = editor.canPaste)
                    DropdownMenuItem(text = { Text(stringResource(R.string.pages)) }, onClick = {
                        more = false
                        pages = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.background)) }, onClick = {
                        more = false
                        background = true
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.favorite_tools)) }, onClick = {
                        more = false
                        showPresets = true
                    })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.take_photo)) },
                        enabled = !exporting,
                        onClick = {
                            more = false
                            onCamera()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_image)) },
                        enabled = !exporting,
                        onClick = {
                            more = false
                            onImage()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.import_pdf)) },
                        enabled = !exporting,
                        onClick = {
                            more = false
                            onPdf()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_jpeg)) },
                        enabled = !exporting,
                        onClick = {
                            more = false
                            onJpegExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.export_pdf)) },
                        enabled = !exporting,
                        onClick = {
                            more = false
                            onPdfExport()
                        }
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.export_png)) }, enabled = !exporting, onClick = {
                        more = false
                        onExport()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.share_board)) }, enabled = !exporting, onClick = {
                        more = false
                        onShare()
                    })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = {
                        more = false
                        onSettings()
                    })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        if (!compact) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { pages = true }) {
                    Icon(Icons.Outlined.Layers, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${editor.page.name} · ${editor.pages.indexOfFirst { it.id == editor.page.id } + 1}/${editor.pages.size}")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.local_only),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { context ->
                    WhiteboardView(context, assets).also {
                        nativeView = it
                        it.onEvent = viewModel::canvas
                        it.onBrowseObjects = { objects = true }
                        it.accessibilitySnapshot = { viewport ->
                            val snapshot = viewModel.state.value.editor
                            CanvasAccessibilitySnapshot(
                                snapshot?.page?.id.orEmpty(),
                                snapshot?.page?.elements.orEmpty(),
                                viewport,
                                snapshot?.selectedIds.orEmpty()
                            )
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.fingerDrawing = fingerDrawing
                    view.snapEnabled = snap
                    view.update(editor.page, editor.tool, editor.ink, editor.selectedIds, viewModel.currentViewport(editor.page.id))
                    if (editor.initialFit) view.requestInitialFit()
                },
                onRelease = {
                    it.onEvent = {}
                    it.onBrowseObjects = {}
                    it.accessibilitySnapshot = null
                    nativeView = null
                }
            )
            if (editor.page.elements.isEmpty() && !compact) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 72.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(stringResource(R.string.canvas_welcome), fontWeight = FontWeight.Medium)
                    Text(
                        stringResource(R.string.canvas_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(14.dp), shape = RoundedCornerShape(14.dp), shadowElevation = 2.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { nativeView?.zoomBy(0.8f) }) { Icon(Icons.Outlined.Remove, stringResource(R.string.zoom_out)) }
                    val resetZoomLabel = stringResource(R.string.reset_zoom)
                    TextButton(onClick = {
                        nativeView?.resetCamera()
                    }, modifier = Modifier.semantics { contentDescription = resetZoomLabel }) { Text("1:1") }
                    IconButton(onClick = { nativeView?.zoomBy(1.25f) }) { Icon(Icons.Outlined.Add, stringResource(R.string.zoom_in)) }
                    IconButton(onClick = {
                        nativeView?.fitContent()
                    }) { Icon(Icons.Outlined.FitScreen, stringResource(R.string.fit_content)) }
                }
            }
            if (exporting) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Surface(shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                if (editor.selectedIds.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SelectionActions(editor, viewModel, onProperties = { properties = true })
                        IconButton(onClick = { viewModel.duplicateSelected() }) {
                            Icon(Icons.Outlined.ContentCopy, stringResource(R.string.duplicate))
                        }
                        IconButton(onClick = { viewModel.deleteSelected() }) {
                            Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.delete))
                        }
                        if (editor.page.elements.firstOrNull { it.id == editor.selectedId } is MediaElement) {
                            IconButton(onClick = { viewModel.rotateSelected() }) {
                                Icon(Icons.AutoMirrored.Outlined.RotateRight, stringResource(R.string.rotate))
                            }
                            IconButton(onClick = { crop = true }) { Icon(Icons.Outlined.Crop, stringResource(R.string.crop)) }
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ToolButton(Icons.Outlined.NearMe, stringResource(R.string.select), editor.tool == Tool.SELECT) {
                        viewModel.tool(Tool.SELECT)
                    }
                    ToolButton(Icons.Outlined.PanTool, stringResource(R.string.pan), editor.tool == Tool.PAN) { viewModel.tool(Tool.PAN) }
                    ToolButton(Icons.Outlined.Edit, stringResource(R.string.pen), editor.tool == Tool.PEN) { viewModel.tool(Tool.PEN) }
                    ToolButton(Icons.Outlined.FormatColorFill, stringResource(R.string.fill_tool), editor.tool == Tool.FILL) {
                        viewModel.tool(Tool.FILL)
                    }
                    ToolButton(Icons.Outlined.Brush, stringResource(R.string.marker), editor.tool == Tool.MARKER) {
                        viewModel.tool(Tool.MARKER)
                    }
                    ToolButton(Icons.Outlined.BorderColor, stringResource(R.string.highlighter), editor.tool == Tool.HIGHLIGHTER) {
                        viewModel.tool(Tool.HIGHLIGHTER)
                    }
                    ToolButton(Icons.Outlined.AutoFixNormal, stringResource(R.string.eraser), editor.tool == Tool.ERASER) {
                        viewModel.tool(Tool.ERASER)
                    }
                    Box {
                        ToolButton(Icons.Outlined.Category, stringResource(R.string.shapes), editor.tool in shapeTools) { shapes = true }
                        DropdownMenu(shapes, { shapes = false }) {
                            shapeTools.forEach { tool ->
                                DropdownMenuItem(text = { Text(stringResource(toolLabel(tool))) }, onClick = {
                                    viewModel.tool(tool)
                                    shapes =
                                        false
                                })
                            }
                        }
                    }
                    ToolButton(Icons.Outlined.Title, stringResource(R.string.text), editor.tool == Tool.TEXT) { viewModel.tool(Tool.TEXT) }
                    ToolButton(Icons.AutoMirrored.Outlined.StickyNote2, stringResource(R.string.note), editor.tool == Tool.NOTE) {
                        viewModel.tool(Tool.NOTE)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorPickerButton(editor.ink.color, favoriteColors, viewModel::favoriteColors) {
                        viewModel.ink(editor.ink.copy(color = it))
                    }
                    Row(
                        Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (listOf(editor.ink.color) + inkColors + favoriteColors).distinct().forEach { color ->
                            ColorButton(color, editor.ink.color == color) { viewModel.ink(editor.ink.copy(color = color)) }
                        }
                        Spacer(Modifier.width(10.dp))
                        val widthLabel = stringResource(R.string.width)
                        val opacityLabel = stringResource(R.string.opacity)
                        Text(widthLabel, style = MaterialTheme.typography.labelSmall)
                        Slider(value = editor.ink.width, onValueChange = {
                            viewModel.ink(editor.ink.copy(width = it))
                        }, valueRange = 1f..40f, modifier = Modifier.width(110.dp).semantics { contentDescription = widthLabel })
                        Text("${editor.ink.width.toInt()}", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.opacity), style = MaterialTheme.typography.labelSmall)
                        Slider(
                            value = editor.ink.opacity,
                            onValueChange = { viewModel.ink(editor.ink.copy(opacity = it)) },
                            valueRange =
                            0.1f..1f,
                            modifier = Modifier.width(90.dp).semantics { contentDescription = opacityLabel }
                        )
                    }
                }
                if (editor.tool == Tool.FILL) {
                    Text(
                        stringResource(R.string.fill_hint),
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    if (pages) PagesDialog(editor, viewModel) { pages = false }
    if (objects) {
        ObjectNavigatorDialog(editor.page, { objects = false }) { id ->
            viewModel.canvas(dev.whiteboard.canvas.CanvasEvent.Select(setOf(id)))
            nativeView?.revealElement(id)
            objects = false
        }
    }
    if (properties) {
        val elements = editor.page.elements.filter { it.id in editor.selectedIds }.flatMap { it.leaves() }
        ObjectStyleDialog(elements, { properties = false }, favoriteColors, viewModel::favoriteColors) { ink, fill ->
            viewModel.applySelectedStyle(ink, fill)
            properties = false
        }
    }
    if (background) BackgroundDialog(editor.page, viewModel, favoriteColors) { background = false }
    if (showPresets) {
        BrushDialog(
            brushes,
            editor.tool in BrushPreset.DRAWING_TOOLS,
            { showPresets = false },
            onSelect = {
                viewModel.preset(it)
                showPresets = false
            },
            onSave = { viewModel.savePreset(it) },
            onDelete = { viewModel.deletePreset(it) }
        )
    }
    if (crop) {
        val selected = editor.page.elements.firstOrNull { it.id == editor.selectedId } as? MediaElement
        if (selected != null) {
            CropDialog(selected, { crop = false }) {
                viewModel.cropSelected(it)
                crop = false
            }
        }
    }
}

private val shapeTools = listOf(Tool.RECTANGLE, Tool.SQUARE, Tool.CIRCLE, Tool.ELLIPSE, Tool.TRIANGLE, Tool.DIAMOND, Tool.LINE, Tool.ARROW)
private fun toolLabel(tool: Tool) = when (tool) {
    Tool.RECTANGLE -> R.string.rectangle
    Tool.SQUARE -> R.string.square
    Tool.CIRCLE -> R.string.circle
    Tool.ELLIPSE -> R.string.ellipse
    Tool.TRIANGLE -> R.string.triangle
    Tool.DIAMOND -> R.string.diamond
    Tool.LINE -> R.string.line
    else -> R.string.arrow
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {
            contentDescription = label
            this.selected = selected
        },
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                icon,
                null,
                Modifier.size(22.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (LocalConfiguration.current.screenHeightDp >= 500) {
                Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
}
