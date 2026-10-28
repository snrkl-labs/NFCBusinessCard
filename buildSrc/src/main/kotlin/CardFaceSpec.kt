/**
 * The Card Face geometry, in one place.
 *
 * These numbers are the single source of truth for three things that must agree:
 * the size the Compose layout draws the card at, the pixel dimensions of the
 * generated placeholder PNG, and the number printed on the placeholder itself.
 * They are defined here, in the build, and everything else is generated from
 * them — so the dimensions written on the image can never be a stale claim.
 *
 * ## How the width was chosen
 *
 * The target device is a Pixel 8 Pro on GrapheneOS: 1344 physical pixels across,
 * reporting `densityDpi` 480, which is a density scale of 3.0 and therefore
 * 448dp of usable width.
 *
 * Taking Material's 16dp margins off both sides leaves 416dp. The card must hold
 * the 90:55 aspect *exactly* — 90:55 reduces to 18:11, so both edge lengths have
 * to be whole multiples of 18 and 11 respectively, or the ratio carries a
 * rounding error that shows up as a half-pixel seam along one edge.
 *
 * 416dp × 3.0 = 1248px, and 1248 is not a multiple of 18. The next multiple down
 * is 69 × 18 = 1242px, which is 414dp. That leaves 17dp margins rather than 16 —
 * a dp of asymmetry against the rest of the layout, traded for an exact ratio and
 * an exact 1:1 pixel mapping. The trade is worth it: the entire point of this
 * view is that someone can read a phone number off it, and resampling small type
 * is precisely what destroys that.
 *
 * So: **1242 × 759 px**, displayed at **414 × 253 dp**, one source pixel per
 * screen pixel on the target device, no upscaling, no filtering.
 */
object CardFaceSpec {

    /** The width the Compose layout caps the card region at. */
    const val WIDTH_DP: Int = 414

    /** Density scale of the target device (Pixel 8 Pro, densityDpi 480). */
    const val TARGET_DENSITY: Int = 3

    /** 414dp × 3.0. A multiple of 18, so the 90:55 ratio divides exactly. */
    const val WIDTH_PX: Int = WIDTH_DP * TARGET_DENSITY

    /**
     * The supported card shapes.
     *
     * Height is derived from the width and the ratio, so changing the width
     * changes every placeholder consistently. Only the Australian default
     * divides exactly; the other two round to the nearest pixel, which is a
     * sub-0.1% error on the aspect and invisible, while the width — the
     * dimension that governs the 1:1 mapping — stays exact for all three.
     */
    enum class Aspect(
        val id: String,
        val widthUnits: Double,
        val heightUnits: Double,
        val label: String,
        /**
         * Whether the app ships real sample artwork at this ratio.
         *
         * Where it does, generating a diagnostic placeholder as well would put
         * an unused bitmap in the APK — the app never has cause to draw it, and
         * lint is right to flag it. The other ratios still get one, because
         * falling back to a silently letterboxed card would be worse than one
         * that announces its own geometry.
         */
        val hasSampleArtwork: Boolean = false,
    ) {
        Australian("au", 90.0, 55.0, "Australian 90 x 55", hasSampleArtwork = true),
        Iso("iso", 85.6, 54.0, "ISO/CR80 85.6 x 54"),
        Us("us", 3.5, 2.0, "US 3.5 x 2 in"),
        ;

        val heightPx: Int
            get() = Math.round(WIDTH_PX * heightUnits / widthUnits).toInt()

        val heightDp: Int
            get() = Math.round(WIDTH_DP * heightUnits / widthUnits).toInt()

        /** Android resource name for the generated placeholder. */
        val drawableName: String
            get() = "card_face_placeholder_$id"
    }

    /** The shape used when the operator has not chosen otherwise. */
    val DEFAULT: Aspect = Aspect.Australian
}
