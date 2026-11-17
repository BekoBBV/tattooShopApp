/**
 * A coil tattoo machine, drawn as a line drawing in the flash sheet's style, held at a slant with its
 * needle finishing a short red wave line (the line draws itself once as the page opens; see the CSS).
 *
 * The machine is drawn upright with the needle tip at 0,0 and then turned and moved so the tip lands
 * exactly where the red line ends. The tube runs along x = 0; the frame and coils sit behind it
 * (negative x). Shapes are filled with the page's black, so parts in front hide the parts behind,
 * the way a line drawing would.
 */
const TIP = { x: 118, y: 294 }
const TILT = 24

export function TattooMachine() {
  return (
    <svg className="tattoo-machine" viewBox="0 0 260 320" aria-hidden="true" focusable="false">
      {/* The line the needle is finishing: a small wave crest, its tail running into the tip */}
      <path
        className="tattoo-machine-line"
        pathLength={1}
        d={`M8 308 C26 308 40 292 54 281 C66 272 82 272 87 282 C90 290 83 297 77 293 C72 290 74 283 81 283 C94 283 104 291 ${TIP.x} ${TIP.y}`}
      />
      <g className="tattoo-machine-body" transform={`translate(${TIP.x} ${TIP.y}) rotate(${TILT})`}>
        {/* clip cord from the rear post, one loose curve */}
        <path className="is-cord" d="M-108 -200 C-140 -190 -150 -150 -132 -126 C-118 -108 -112 -92 -126 -74" />

        {/* frame: rear post, the yoke under the coils, and the front upright with the screw holder */}
        <rect x="-114" y="-250" width="11" height="90" />
        <rect x="-114" y="-171" width="96" height="11" />
        <rect x="-30" y="-264" width="11" height="104" />
        <rect x="-30" y="-272" width="26" height="9" />

        {/* the two coils: washers top and bottom, wire wound between */}
        {[-100, -66].map((x) => (
          <g key={x}>
            <rect x={x} y="-226" width="28" height="55" />
            {[-221, -216, -211, -206, -201, -196, -191, -186, -181, -176].map((y) => (
              <line key={y} className="is-wire" x1={x} y1={y} x2={x + 28} y2={y} />
            ))}
            <rect x={x - 3} y="-231" width="34" height="5" />
          </g>
        ))}

        {/* armature bar across the coil tops, the rear spring up to the post, the front spring on top */}
        <rect x="-104" y="-243" width="104" height="8" />
        <path className="is-spring" d="M-109 -250 C-109 -244 -106 -241 -98 -241" />
        <path className="is-spring" d="M-46 -245 C-32 -247 -18 -249 -2 -251" />

        {/* contact screw down through the holder to the front spring, knurled knob on top */}
        <line x1="-14" y1="-286" x2="-14" y2="-250" />
        <rect x="-21" y="-298" width="14" height="13" />
        <line x1="-21" y1="-293" x2="-7" y2="-293" />
        <line x1="-21" y1="-289" x2="-7" y2="-289" />

        {/* needle bar from the front of the armature down into the tube */}
        <line x1="0" y1="-236" x2="0" y2="-170" />
        <circle cx="0" cy="-238" r="4" />

        {/* tube vise, tube, knurled grip, tip and needle */}
        <rect x="-24" y="-172" width="34" height="13" />
        <rect x="-7" y="-159" width="14" height="47" />
        <rect x="-12" y="-112" width="24" height="72" />
        {[-104, -97, -90, -83, -76, -69, -62, -55, -48].map((y) => (
          <line key={y} x1="-12" y1={y} x2="12" y2={y} />
        ))}
        <path d="M-12 -40 L-4 -9 H4 L12 -40 Z" />
        <line className="is-needle" x1="0" y1="-9" x2="0" y2="0" />
      </g>
      <circle className="tattoo-machine-drop" cx={TIP.x} cy={TIP.y} r="2.4" />
    </svg>
  )
}
