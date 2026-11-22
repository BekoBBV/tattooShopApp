/**
 * The pictures on the page-title scrolls, drawn for the site as ink line drawings over a flat red
 * sun, the way flash is coloured: one colour laid flat, the outline drawn over it. They use the
 * site's three line weights (the CSS sets them: outline, inner line, fine detail), and the shapes
 * are filled with the canvas colour so the lines in front cover the sun behind.
 * Each is 100 wide and 200 tall, the shape of the scroll's canvas.
 */
export type ScrollArtKind = "lantern" | "pagoda" | "seal"

/** The ground both pictures stand on: a horizon line and a few shorter lines under it */
function Ground() {
  return (
    <g className="art-ground">
      <path className="line-outline" d="M6 168 H94" />
      <path className="line-detail" d="M14 173 H40 M58 173 H86 M24 178 H70 M38 183 H62" />
    </g>
  )
}

/** A stone lantern, its window lit, with the sun low behind it */
function Lantern() {
  return (
    <>
      <circle className="art-sun" cx="38" cy="74" r="30" />
      <g className="art-ink">
        {/* jewel on top and the roof, its corners turned up */}
        <path className="line-outline" d="M56 52 C53 56 52.5 59 53 61 A3.1 3.1 0 0 0 59 61 C59.5 59 59 56 56 52 Z" />
        <rect className="line-inner" x="53.5" y="62.5" width="5" height="2.5" />
        <path className="line-outline" d="M29 79 C36 80 44 72 56 65 C68 72 76 80 83 79 L80 85 H32 Z" />
        <path className="line-detail" d="M56 67 L44 84 M56 67 L68 84 M56 67 V84" />
        {/* firebox, with the paper window and its frame */}
        <rect className="line-outline" x="43" y="85" width="26" height="20" />
        <rect className="art-window line-inner" x="49" y="89" width="14" height="12" />
        <path className="line-detail" d="M56 89 V101 M49 95 H63" />
        {/* platform, post with its ring, and the two-step base */}
        <path className="line-outline" d="M37 105 H75 L72 110 H40 Z" />
        <rect className="line-outline" x="51" y="110" width="10" height="44" />
        <rect className="line-inner" x="49.5" y="128" width="13" height="3.5" />
        <path className="line-detail" d="M54 113 V126 M54 134 V151" />
        <path className="line-outline" d="M44 154 H68 L71 160 H41 Z" />
        <rect className="line-outline" x="36" y="160" width="40" height="8" />
      </g>
      <Ground />
    </>
  )
}

/** Five-storey pagoda, the sun behind it */
function Pagoda() {
  // From the bottom storey up: each roof a little narrower, the walls under it narrower still
  const storeys = [0, 1, 2, 3, 4].map((i) => {
    const roofTop = 139 - i * 17
    const w = 31 - i * 4.2
    const wall = w * 0.6
    return { roofTop, w, wall, wallTop: roofTop + 6, wallBottom: i === 0 ? 159 : 139 - (i - 1) * 17 }
  })
  return (
    <>
      <circle className="art-sun" cx="50" cy="100" r="36" />
      <g className="art-ink">
        {/* spire with its rings and the jewel on top */}
        <path className="line-inner" d="M50 50 V74" />
        <path className="line-detail" d="M46.8 55 H53.2 M46.8 59 H53.2 M46.8 63 H53.2 M46.8 67 H53.2" />
        <circle className="line-inner" cx="50" cy="48.5" r="2" />
        {storeys.map(({ roofTop: t, w, wall, wallTop, wallBottom }) => (
          <g key={t}>
            {/* walls, with their corner posts and a doorway */}
            <rect className="line-outline" x={50 - wall} y={wallTop} width={wall * 2} height={wallBottom - wallTop} />
            <path className="line-detail" d={`M${50 - wall + 2} ${wallTop} V${wallBottom} M${50 + wall - 2} ${wallTop} V${wallBottom}`} />
            <rect className="line-detail" x={50 - wall * 0.3} y={wallTop + 3} width={wall * 0.6} height={wallBottom - wallTop - 3} />
            {/* roof, its eaves turned up at the ends, with the line of the tiles under the ridge */}
            <path className="line-outline" d={`M${50 - w - 2} ${t - 1.5} Q50 ${t + 3} ${50 + w + 2} ${t - 1.5} L${50 + w - 3} ${t + 6} H${50 - w + 3} Z`} />
            <path className="line-detail" d={`M${50 - w + 2} ${t + 2} Q50 ${t + 5} ${50 + w - 2} ${t + 2}`} />
          </g>
        ))}
        <rect className="line-outline" x="26" y="159" width="48" height="5" />
        <rect className="line-outline" x="20" y="164" width="60" height="4" />
      </g>
      <Ground />
    </>
  )
}

/**
 * The studio's own scroll: the red BB seal where the other pictures have their sun, and the name
 * written under it in ink, with a fine rule and the city
 */
function Seal() {
  return (
    <>
      <image className="art-seal" href="/images/seal-bb.png" x="22" y="40" width="56" height="56" />
      <g className="art-ink art-lettering">
        <text x="50" y="124">Blade &amp;</text>
        <text x="50" y="140">Brush</text>
        <path className="line-detail" d="M36 150 H64" />
        <text className="art-city" x="50" y="163">Sofia</text>
      </g>
    </>
  )
}

export function ScrollArt({ kind }: { kind: ScrollArtKind }) {
  return (
    <svg className="scroll-art" viewBox="0 0 100 200" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      {kind === "lantern" ? <Lantern /> : kind === "pagoda" ? <Pagoda /> : <Seal />}
    </svg>
  )
}
