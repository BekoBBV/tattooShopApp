export const shop = {
  address: "14 Neon Alley, Kapana, Plovdiv 4000",
  phone: "+359 32 555 014",
  email: "hello@tattoostudio.demo",
  timezone: "Europe/Sofia",
}

/** A tel: link for a phone number as people write it, e.g. "+359 32 555 014". */
export const telHref = (phone: string) => `tel:${phone.replace(/\s/g, "")}`

export const formatMoney =(amount: number) =>
  new Intl.NumberFormat("bg-BG", {
    style: "currency",
    currency: "EUR",
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(amount)

export const formatAppointment = (iso: string) =>
  new Intl.DateTimeFormat("en-GB", {
    weekday: "short",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
    timeZone: shop.timezone,
  }).format(new Date(iso))
