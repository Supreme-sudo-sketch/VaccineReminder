// Every reminder asks the caregiver to go to the child's hospital or the hospital nearest them (`p`,
// from where() below) and to carry the immunisation card.
const T = {
  en: {
    before: (c, v, d, p) => `Reminder: ${c} is due for ${v} on ${d}. Please go to ${p} and carry the immunisation card.`,
    due: (c, v, d, p) => `Today is vaccination day for ${c}: ${v}. Please go to ${p} and carry the immunisation card.`,
    overdue: (c, v, d, p) => `${c} missed ${v} due on ${d}. Please go to ${p} as soon as you can and carry the immunisation card. It is not too late.`,
    followup: (c, v, d, p) =>
      `Second reminder: ${c} has not yet received ${v} (due ${d}). Please go to ${p} soon and carry the immunisation card. The nurse will help your child catch up.`,
  },
  sw: {
    before: (c, v, d, p) => `Kumbusho: ${c} anatakiwa kupata chanjo ya ${v} tarehe ${d}. Tafadhali fika ${p}, na ubebe kadi ya chanjo.`,
    due: (c, v, d, p) => `Leo ni siku ya chanjo ya ${c}: ${v}. Tafadhali fika ${p}, na ubebe kadi ya chanjo.`,
    overdue: (c, v, d, p) =>
      `${c} amekosa chanjo ya ${v} iliyotakiwa tarehe ${d}. Tafadhali fika ${p} haraka iwezekanavyo, na ubebe kadi ya chanjo. Bado hujachelewa.`,
    followup: (c, v, d, p) =>
      `Kumbusho la pili: ${c} bado hajapata chanjo ya ${v} (tarehe ${d}). Tafadhali fika ${p} hivi karibuni, na ubebe kadi ya chanjo. Muuguzi atamsaidia mtoto kufidia chanjo.`,
  },
};

// Where to go: the hospital the child is registered at, or the one nearest the family's home. When the
// health worker did not record the nearest one, any hospital near the caregiver will do.
const PLACE = {
  en: {
    both: (f, n) => `${f} or ${n} (the hospital nearest you)`,
    facility: (f) => `${f} or the hospital nearest you`,
    nearest: (n) => `${n} (the hospital nearest you)`,
    none: () => 'the hospital nearest you',
  },
  sw: {
    both: (f, n) => `${f} au ${n} (hospitali iliyo karibu nawe)`,
    facility: (f) => `${f} au hospitali iliyo karibu nawe`,
    nearest: (n) => `${n} (hospitali iliyo karibu nawe)`,
    none: () => 'hospitali iliyo karibu nawe',
  },
};

function where(lang, facility, nearest) {
  const t = PLACE[lang] || PLACE.en;
  const f = String(facility || '').trim();
  let n = String(nearest || '').trim();
  if (n.toLowerCase() === f.toLowerCase()) n = '';
  if (f && n) return t.both(f, n);
  if (f) return t.facility(f);
  if (n) return t.nearest(n);
  return t.none();
}

// `facility` is the hospital the child is registered at and `nearest` the one nearest the family's home.
export function buildMessage(kind, lang, childName, vaccines, dueDate, facility, nearest = '') {
  const set = T[lang] || T.en;
  return set[kind](childName, vaccines.join(', '), dueDate, where(lang, facility, nearest));
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

// App notifications telling the caregiver how a health worker reviewed their vaccination evidence.
const VERIFIED = {
  en: {
    verified: (c, v, d) => `Verified: ${c}'s ${v} given on ${d} is now in the vaccination record. Thank you for sending the evidence.`,
    rejected: (c, v, d, r) =>
      `We could not verify the evidence for ${c}'s ${v}. Reason: ${r} Open the app to send corrected evidence.`,
  },
  sw: {
    verified: (c, v, d) => `Imethibitishwa: chanjo ya ${v} ya ${c} iliyotolewa tarehe ${d} sasa imo kwenye rekodi ya chanjo. Asante kwa kutuma ushahidi.`,
    rejected: (c, v, d, r) =>
      `Hatukuweza kuthibitisha ushahidi wa chanjo ya ${v} ya ${c}. Sababu: ${r} Fungua programu kutuma ushahidi uliorekebishwa.`,
  },
};

export function verificationMessage(outcome, lang, childName, vaccine, date, reason = '') {
  const set = VERIFIED[lang] || VERIFIED.en;
  const why = /[.!?]$/.test(reason) ? reason : `${reason}.`;
  return set[outcome](childName, vaccine, date, why);
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
