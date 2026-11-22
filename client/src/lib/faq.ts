import { studio } from "./format"

/** The questions people ask before their first tattoo (on the Aftercare page). */
export const FAQ = [
  { q: "Does it hurt?", a: "Yes, a little. Most people describe it as a hot scratch. Ribs, feet and the inner arm hurt more than the forearm, calf or thigh. You can take a break whenever you need one." },
  { q: "How clean is the studio?", a: "Needles, ink caps and gloves are single-use and opened in front of you. The station is disinfected and covered in fresh film before every client." },
  { q: "What should I bring?", a: "A photo ID that shows you are 18 or older. Eat beforehand, and wear clothes that leave the spot free. For a custom piece, bring your reference pictures." },
  { q: "Do you take walk-ins?", a: "Sometimes. If an artist has a free hour, they can tattoo a small flash design the same day, so call the studio first. To be sure of a time, book online." },
  { q: "How do I pay?", a: `For a flash design you pay a €30 deposit online when you book, with Revolut, a card, Apple Pay or Google Pay. It is taken off the price, and the rest you pay in the studio ${studio.payment}. For a custom piece, your artist tells you the price of each session and its deposit.` },
  { q: "Can I bring my own design?", a: "Yes. Book a free consultation and your artist will redraw your idea in their own style. We don't copy another artist's tattoo." },
  { q: "How long does healing take?", a: "The surface heals in two to four weeks and the skin settles within a month or two. The healing guide above walks you through each day." },
  { q: "Can you cover an old tattoo?", a: "Often, yes. Bring photos to a free consultation and the artist will tell you what is possible." },
]
