package com.example.unpawse.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.unpawse.data.settings.CatAvatar
import com.example.unpawse.data.settings.catAvatarForId
import com.example.unpawse.ui.theme.UnPawseTheme

/**
 * The preset cat avatars, drawn with Canvas rather than shipped as assets: eight faces cost no APK
 * size, scale to any avatar slot without a density bucket, and — the reason that actually decided
 * it — a stored `Int` needs no file to back up, export, or clean up later.
 *
 * Colors are fixed rather than Material roles. This is identity, the same argument
 * `UnPawseExtendedColors` makes for the usage categories: a ginger cat that turned plum in dark
 * mode would read as a different cat.
 */
@Composable
fun CatAvatarImage(
    avatar: CatAvatar,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    val fur = furFor(avatar)
    Canvas(modifier = modifier.size(size).clip(CircleShape)) { drawCatFace(fur) }
}

/**
 * The user's profile picture: their chosen cat, or [InitialsAvatar] when they haven't chosen one.
 * Every avatar slot should go through this rather than branching itself, so "not chosen yet" and
 * "an id this build can't draw" (see `catAvatarForId`) land on the same fallback.
 */
@Composable
fun ProfileAvatar(
    avatarId: Int,
    initial: Char,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val avatar = catAvatarForId(avatarId)
    if (avatar == null) {
        InitialsAvatar(initial = initial, modifier = modifier, size = size)
    } else {
        CatAvatarImage(avatar = avatar, modifier = modifier, size = size)
    }
}

/** How one preset is colored. [patch] is the second coat and [pattern] says where it goes. */
private data class CatFur(
    val background: Color,
    val coat: Color,
    val patch: Color?,
    val pattern: CatPattern,
    val earInner: Color,
    val eye: Color,
    val nose: Color,
)

private enum class CatPattern {
    /** One coat all over. */
    SOLID,

    /** A darker mask over the top of the face, Siamese-style. */
    MASK,

    /** A patch down one side of the face, calico/tabby-style. */
    SIDE,

    /** A pale bib across the chin and muzzle, tuxedo-style. */
    BIB,
}

private val Charcoal = Color(0xFF2E2226)
private val Blush = Color(0xFFF5B6C8)
private val Cream = Color(0xFFF6EBEC)

private fun furFor(avatar: CatAvatar): CatFur = when (avatar) {
    CatAvatar.CREAM -> CatFur(
        background = Color(0xFFFFF1E4), coat = Color(0xFFF1D9BC), patch = null,
        pattern = CatPattern.SOLID, earInner = Blush,
        eye = Color(0xFF6B8E6B), nose = Color(0xFFE79FB0),
    )
    CatAvatar.GINGER -> CatFur(
        background = Color(0xFFFFE7D6), coat = Color(0xFFE68A4E), patch = Color(0xFFC96B33),
        pattern = CatPattern.SIDE, earInner = Color(0xFFF7C4A8),
        eye = Color(0xFF4F7A3F), nose = Color(0xFFD1685F),
    )
    CatAvatar.TABBY -> CatFur(
        background = Color(0xFFF1EDE6), coat = Color(0xFFA79280), patch = Color(0xFF7C6A5C),
        pattern = CatPattern.SIDE, earInner = Blush,
        eye = Color(0xFFC59B36), nose = Color(0xFFC98C93),
    )
    CatAvatar.TUXEDO -> CatFur(
        background = Color(0xFFEDEAF0), coat = Color(0xFF3A3038), patch = Cream,
        pattern = CatPattern.BIB, earInner = Color(0xFF8E6C77),
        eye = Color(0xFFE0C24E), nose = Blush,
    )
    CatAvatar.SIAMESE -> CatFur(
        background = Color(0xFFF6EFE4), coat = Color(0xFFE8DCC7), patch = Color(0xFF8B7460),
        pattern = CatPattern.MASK, earInner = Color(0xFFC79E96),
        eye = Color(0xFF5B8FC7), nose = Color(0xFF9B7566),
    )
    CatAvatar.CALICO -> CatFur(
        background = Color(0xFFFDECEF), coat = Cream, patch = Color(0xFFE08A4A),
        pattern = CatPattern.SIDE, earInner = Blush,
        eye = Color(0xFF6E8B4F), nose = Color(0xFFE79FB0),
    )
    CatAvatar.SMOKE -> CatFur(
        background = Color(0xFFECEEF1), coat = Color(0xFF9BA3AC), patch = null,
        pattern = CatPattern.SOLID, earInner = Blush,
        eye = Color(0xFF3F8F86), nose = Color(0xFFB98A93),
    )
    CatAvatar.MIDNIGHT -> CatFur(
        background = Color(0xFFE7E3EC), coat = Color(0xFF33293A), patch = null,
        pattern = CatPattern.SOLID, earInner = Color(0xFF7E5F6E),
        eye = Color(0xFFE9C55B), nose = Color(0xFF9C7684),
    )
}

/**
 * Draws one face across the whole draw area. Every measurement is a fraction of the smaller side,
 * so the same code renders a 32dp settings row and a 120dp onboarding hero.
 */
private fun DrawScope.drawCatFace(fur: CatFur) {
    val d = size.minDimension
    val cx = size.width / 2f
    val cy = size.height / 2f
    val head = d * 0.32f
    val headCenter = Offset(cx, cy + d * 0.05f)

    drawCircle(color = fur.background, radius = d / 2f, center = Offset(cx, cy))

    // Ears first, so the head circle covers where they join it and no seam shows.
    drawEar(fur, headCenter, head, leftSide = true)
    drawEar(fur, headCenter, head, leftSide = false)

    drawCircle(color = fur.coat, radius = head, center = headCenter)

    val headRect = Rect(center = headCenter, radius = head)
    val patch = fur.patch
    if (patch != null) {
        clipPath(Path().apply { addOval(headRect) }) {
            when (fur.pattern) {
                CatPattern.SOLID -> Unit
                // A mask sits high on the face, so an oval hung above the center reads as one.
                CatPattern.MASK -> drawOval(
                    color = patch,
                    topLeft = Offset(headCenter.x - head * 0.72f, headCenter.y - head * 1.15f),
                    size = Size(head * 1.44f, head * 1.5f),
                )
                CatPattern.SIDE -> drawRect(
                    color = patch,
                    topLeft = Offset(headRect.left, headRect.top),
                    size = Size(head * 0.72f, headRect.height),
                )
                CatPattern.BIB -> drawOval(
                    color = patch,
                    topLeft = Offset(headCenter.x - head * 0.55f, headCenter.y - head * 0.05f),
                    size = Size(head * 1.1f, head * 1.4f),
                )
            }
        }
    }

    val eyeDx = head * 0.42f
    val eyeDy = head * 0.12f
    val eyeR = head * 0.16f
    drawCircle(fur.eye, eyeR, Offset(headCenter.x - eyeDx, headCenter.y - eyeDy))
    drawCircle(fur.eye, eyeR, Offset(headCenter.x + eyeDx, headCenter.y - eyeDy))
    // A vertical slit would vanish at avatar sizes; a dot is the honest read at 32dp.
    drawCircle(Charcoal, eyeR * 0.45f, Offset(headCenter.x - eyeDx, headCenter.y - eyeDy))
    drawCircle(Charcoal, eyeR * 0.45f, Offset(headCenter.x + eyeDx, headCenter.y - eyeDy))

    val noseWidth = head * 0.16f
    val noseY = headCenter.y + head * 0.3f
    drawPath(
        path = Path().apply {
            moveTo(headCenter.x - noseWidth, noseY - noseWidth * 0.6f)
            lineTo(headCenter.x + noseWidth, noseY - noseWidth * 0.6f)
            lineTo(headCenter.x, noseY + noseWidth * 0.7f)
            close()
        },
        color = fur.nose,
    )

    val whiskerInk = Charcoal.copy(alpha = 0.35f)
    val whiskerWidth = d * 0.012f
    val whiskerY = headCenter.y + head * 0.36f
    listOf(-1f, 1f).forEach { side ->
        listOf(-head * 0.12f, head * 0.1f).forEach { dy ->
            drawLine(
                color = whiskerInk,
                start = Offset(headCenter.x + side * head * 0.3f, whiskerY + dy * 0.5f),
                end = Offset(headCenter.x + side * head * 1.1f, whiskerY + dy),
                strokeWidth = whiskerWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** One ear: a triangle in the coat color with a smaller inner triangle, mirrored by [leftSide]. */
private fun DrawScope.drawEar(fur: CatFur, headCenter: Offset, head: Float, leftSide: Boolean) {
    val side = if (leftSide) -1f else 1f
    val baseX = headCenter.x + side * head * 0.55f
    val baseY = headCenter.y - head * 0.62f
    drawPath(
        path = Path().apply {
            moveTo(baseX - side * head * 0.34f, baseY + head * 0.25f)
            lineTo(baseX + side * head * 0.18f, baseY - head * 0.62f)
            lineTo(baseX + side * head * 0.42f, baseY + head * 0.18f)
            close()
        },
        color = fur.coat,
    )
    drawPath(
        path = Path().apply {
            moveTo(baseX - side * head * 0.14f, baseY + head * 0.24f)
            lineTo(baseX + side * head * 0.16f, baseY - head * 0.34f)
            lineTo(baseX + side * head * 0.30f, baseY + head * 0.2f)
            close()
        },
        color = fur.earInner,
    )
}

@Preview(name = "Cat avatars", showBackground = true, backgroundColor = 0xFFFFF8F8)
@Composable
private fun CatAvatarPreview() {
    UnPawseTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CatAvatar.entries.chunked(4).forEach { rowAvatars ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowAvatars.forEach { CatAvatarImage(avatar = it, size = 72.dp) }
                }
            }
            ProfileAvatar(avatarId = 0, initial = 'S', size = 72.dp)
        }
    }
}
