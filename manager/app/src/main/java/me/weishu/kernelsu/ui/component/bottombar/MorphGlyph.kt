package me.weishu.kernelsu.ui.component.bottombar

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath

private const val HOLE_AREA_RATIO = 0.2f

internal class MorphGlyph private constructor(
    private val contours: List<Contour>,
    private val fillType: PathFillType,
    val viewportWidth: Float,
    val viewportHeight: Float,
) {
    private class Contour(
        val path: Path,
        val bounds: Rect,
        val closedBySelection: Boolean,
    )

    fun pathAt(progress: Float): Path {
        val out = Path()
        out.fillType = fillType
        for (contour in contours) {
            if (!contour.closedBySelection || progress <= 0f) {
                out.addPath(contour.path)
                continue
            }
            val shrink = 1f - progress
            val pivot = contour.bounds.center
            val matrix = Matrix().apply {
                translate(-pivot.x, -pivot.y)
                scale(shrink, shrink)
                translate(pivot.x, pivot.y)
            }
            val scaled = Path()
            scaled.addPath(contour.path)
            scaled.transform(matrix)
            out.addPath(scaled)
        }
        return out
    }

    companion object {
        fun of(vector: ImageVector): MorphGlyph? {
            val shapes = vector.allPaths()
            if (shapes.isEmpty()) return null
            val fillType = shapes.first().pathFillType
            val raw = buildList {
                for (shape in shapes) {
                    for (subpath in shape.pathData.splitSubpaths()) {
                        val path = PathParser().addPathNodes(subpath).toPath()
                        val bounds = path.getBounds()
                        if (!bounds.isEmpty) add(Contour(path, bounds, false))
                    }
                }
            }
            if (raw.isEmpty()) return null
            val contours = raw.mapIndexed { index, contour ->
                val area = contour.bounds.width * contour.bounds.height
                val host = raw.indices.firstOrNull { other ->
                    other != index &&
                        raw[other].bounds.encloses(contour.bounds) &&
                        raw[other].bounds.width * raw[other].bounds.height > area
                }
                val hostArea = host?.let { raw[it].bounds.width * raw[it].bounds.height } ?: 0f
                val closes = hostArea > 0f && area / hostArea >= HOLE_AREA_RATIO
                Contour(contour.path, contour.bounds, closes)
            }
            return MorphGlyph(contours, fillType, vector.viewportWidth, vector.viewportHeight)
        }
    }
}

@Composable
internal fun MorphIcon(
    vector: ImageVector,
    selected: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val glyph = remember(vector) { MorphGlyph.of(vector) }
    if (glyph == null) {
        androidx.compose.material3.Icon(imageVector = vector, contentDescription = null, tint = tint, modifier = modifier)
        return
    }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "navIconMorph",
    )
    Canvas(modifier = modifier) {
        val scaleX = size.width / glyph.viewportWidth
        val scaleY = size.height / glyph.viewportHeight
        scale(scaleX, scaleY, pivot = Offset.Zero) {
            drawPath(glyph.pathAt(progress), color = tint)
        }
    }
}

private fun ImageVector.allPaths(): List<VectorPath> = buildList {
    fun visit(group: VectorGroup) {
        for (node in group) {
            when (node) {
                is VectorPath -> add(node)
                is VectorGroup -> visit(node)
            }
        }
    }
    visit(root)
}

private fun Rect.encloses(other: Rect): Boolean =
    other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

private fun List<PathNode>.splitSubpaths(): List<List<PathNode>> {
    val out = mutableListOf<MutableList<PathNode>>()
    var penX = 0f
    var penY = 0f
    var startX = 0f
    var startY = 0f

    fun begin(px: Float, py: Float) {
        penX = px
        penY = py
        startX = px
        startY = py
        out += mutableListOf<PathNode>(PathNode.MoveTo(px, py))
    }

    for (node in this) {
        if (node is PathNode.MoveTo) {
            begin(node.x, node.y)
            continue
        }
        if (node is PathNode.RelativeMoveTo) {
            begin(penX + node.dx, penY + node.dy)
            continue
        }
        if (out.isEmpty()) begin(0f, 0f)
        out.last() += node
        when (node) {
            is PathNode.LineTo -> {
                penX = node.x
                penY = node.y
            }

            is PathNode.RelativeLineTo -> {
                penX += node.dx
                penY += node.dy
            }

            is PathNode.HorizontalTo -> penX = node.x
            is PathNode.RelativeHorizontalTo -> penX += node.dx
            is PathNode.VerticalTo -> penY = node.y
            is PathNode.RelativeVerticalTo -> penY += node.dy

            is PathNode.CurveTo -> {
                penX = node.x3
                penY = node.y3
            }

            is PathNode.RelativeCurveTo -> {
                penX += node.dx3
                penY += node.dy3
            }

            is PathNode.ReflectiveCurveTo -> {
                penX = node.x2
                penY = node.y2
            }

            is PathNode.RelativeReflectiveCurveTo -> {
                penX += node.dx2
                penY += node.dy2
            }

            is PathNode.QuadTo -> {
                penX = node.x2
                penY = node.y2
            }

            is PathNode.RelativeQuadTo -> {
                penX += node.dx2
                penY += node.dy2
            }

            is PathNode.ReflectiveQuadTo -> {
                penX = node.x
                penY = node.y
            }

            is PathNode.RelativeReflectiveQuadTo -> {
                penX += node.dx
                penY += node.dy
            }

            is PathNode.ArcTo -> {
                penX = node.arcStartX
                penY = node.arcStartY
            }

            is PathNode.RelativeArcTo -> {
                penX += node.arcStartDx
                penY += node.arcStartDy
            }

            is PathNode.Close -> {
                penX = startX
                penY = startY
            }
        }
    }
    return out
}
