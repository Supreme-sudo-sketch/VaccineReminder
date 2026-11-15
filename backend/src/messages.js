const T = {
  en: {
    before: (c, v, d, f) => `Reminder: ${c} is due for ${v} on ${d}${f ? ' at ' + f : ''}. Please bring the immunisation card.`,
    due: (c, v, d, f) => `Today is vaccination day for ${c}: ${v}${f ? '. Please go to ' + f : ''}. Bring the immunisation card.`,
    overdue: (c, v, d, f) => `${c} missed ${v} due on ${d}. Please visit ${f || 'your clinic'} as soon as you can. It is not too late.`,
    followup: (c, v, d, f) => `Second reminder: ${c} has not yet received ${v} (due ${d}). Please visit ${f || 'your clinic'} soon. The nurse will help your child catch up.`,
  },
  sw: {
    before: (c, v, d, f) => `Kumbusho: ${c} anatakiwa kupata chanjo ya ${v} tarehe ${d}${f ? ' katika ' + f : ''}. Tafadhali leta kadi ya chanjo.`,
    due: (c, v, d, f) => `Leo ni siku ya chanjo ya ${c}: ${v}${f ? '. Tafadhali fika ' + f : ''}. Leta kadi ya chanjo.`,
    overdue: (c, v, d, f) => `${c} amekosa chanjo ya ${v} iliyotakiwa tarehe ${d}. Tafadhali tembelea ${f || 'kliniki yako'} haraka iwezekanavyo. Bado hujachelewa.`,
    followup: (c, v, d, f) => `Kumbusho la pili: ${c} bado hajapata chanjo ya ${v} (tarehe ${d}). Tafadhali tembelea ${f || 'kliniki yako'} hivi karibuni. Muuguzi atamsaidia mtoto kufidia chanjo.`,
  },
};

export function buildMessage(kind, lang, childName, vaccines, dueDate, facility) {
  const set = T[lang] || T.en;
  return set[kind](childName, vaccines.join(', '), dueDate, facility);
}

const NEARBY = {
  en: {
    intro: { hospital: 'Nearest hospitals to where you are', clinic: 'Nearest clinics to where you are' },
    km: (d) => `${d.toFixed(1)} km`,
    confirm: 'Call to confirm that the vaccine is available.',
  },
  sw: {
    intro: { hospital: 'Hospitali zilizo karibu na ulipo', clinic: 'Kliniki zilizo karibu na ulipo' },
    km: (d) => `km ${d.toFixed(1)}`,
    confirm: 'Piga simu kuthibitisha kuwa chanjo inapatikana.',
  },
};

export function nearbyClinicsText(lang, clinics, kind = 'clinic') {
  if (!clinics || !clinics.length) return '';
  const t = NEARBY[lang] || NEARBY.en;
  const list = clinics
    .map((c, i) => `${i + 1}) ${c.name}, ${t.km(c.distanceKm)}${c.phone ? ', ' + c.phone : ''}`)
    .join('; ');
  return `\n${t.intro[kind] || t.intro.clinic}: ${list}. ${t.confirm}`;
}

// Each reminder is sent once per child and due date, on the first daily run that falls inside its
// window (days until the due date, negative when late). Windows mean a day with the server down
// does not lose the reminder. Listed in priority order: a child gets at most one SMS per day.
export const KINDS = [
  { kind: 'due', from: 0, to: 0 },
  { kind: 'before', from: 1, to: 3 },
  { kind: 'overdue', from: -13, to: -3 },
  { kind: 'followup', from: -60, to: -14 },
];

export function kindFor(daysUntilDue) {
  return KINDS.find((k) => daysUntilDue >= k.from && daysUntilDue <= k.to) || null;
}
