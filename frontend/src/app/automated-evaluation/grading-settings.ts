export type SpeedBonusMode = 'manual' | 'dynamic'

export interface GradingSettings {
  correctPoints: number
  partialPoints: number
  timeoutSeconds: number
  speedBonusMode: SpeedBonusMode
  fastBonusPoints: number
  fastBonusSeconds: number
  fastBonusZeroSeconds: number
  speedBonusCorrectOnly: boolean
  showGradesInLogs: boolean
}

export const DEFAULT_GRADING_SETTINGS: GradingSettings = {
  correctPoints: 1,
  partialPoints: 0.5,
  timeoutSeconds: 60,
  speedBonusMode: 'manual',
  fastBonusPoints: 0.2,
  fastBonusSeconds: 3,
  fastBonusZeroSeconds: 60,
  speedBonusCorrectOnly: false,
  showGradesInLogs: true
};

export function copyGradingSettings(settings: GradingSettings): GradingSettings {
  return {
    correctPoints: settings.correctPoints,
    partialPoints: settings.partialPoints,
    timeoutSeconds: settings.timeoutSeconds,
    speedBonusMode: settings.speedBonusMode === 'dynamic' ? 'dynamic' : 'manual',
    fastBonusPoints: settings.fastBonusPoints,
    fastBonusSeconds: settings.fastBonusSeconds,
    fastBonusZeroSeconds: settings.fastBonusZeroSeconds,
    speedBonusCorrectOnly: settings.speedBonusCorrectOnly === true,
    showGradesInLogs: settings.showGradesInLogs !== false
  };
}

export function sanitizeGradingSettings(settings: GradingSettings): GradingSettings {
  const timeoutSeconds = clampNumber(settings.timeoutSeconds, 1, 600, DEFAULT_GRADING_SETTINGS.timeoutSeconds);
  const fastBonusSeconds = clampNumber(settings.fastBonusSeconds, 0, 600, DEFAULT_GRADING_SETTINGS.fastBonusSeconds);
  const fastBonusZeroSeconds = Math.max(
    fastBonusSeconds,
    clampNumber(settings.fastBonusZeroSeconds, 0, 600, timeoutSeconds)
  );
  return {
    correctPoints: clampNumber(settings.correctPoints, 0, 100, DEFAULT_GRADING_SETTINGS.correctPoints),
    partialPoints: clampNumber(settings.partialPoints, 0, 100, DEFAULT_GRADING_SETTINGS.partialPoints),
    timeoutSeconds,
    speedBonusMode: settings.speedBonusMode === 'dynamic' ? 'dynamic' : 'manual',
    fastBonusPoints: clampNumber(settings.fastBonusPoints, 0, 100, DEFAULT_GRADING_SETTINGS.fastBonusPoints),
    fastBonusSeconds,
    fastBonusZeroSeconds,
    speedBonusCorrectOnly: settings.speedBonusCorrectOnly === true,
    showGradesInLogs: settings.showGradesInLogs !== false
  };
}

export function speedBonusPoints(
  responseTimeMs: number | null,
  settings: GradingSettings,
  eligible: boolean
): number {
  if (!eligible || responseTimeMs == null || responseTimeMs < 0 || settings.fastBonusPoints <= 0) {
    return 0;
  }
  const seconds = responseTimeMs / 1000;
  if (settings.speedBonusMode === 'manual') {
    return seconds <= settings.fastBonusSeconds ? roundBonus(settings.fastBonusPoints) : 0;
  }
  const fullUntil = settings.fastBonusSeconds;
  const zeroAt = Math.max(settings.fastBonusZeroSeconds, fullUntil);
  if (seconds <= fullUntil) {
    return roundBonus(settings.fastBonusPoints);
  }
  if (seconds >= zeroAt || zeroAt === fullUntil) {
    return 0;
  }
  return roundBonus(settings.fastBonusPoints * (zeroAt - seconds) / (zeroAt - fullUntil));
}

export function formatBonusPoints(points: number): string {
  if (points <= 0) {
    return '';
  }
  return `+${Number(points.toFixed(2))}`;
}

export function speedBonusRulesText(settings: GradingSettings): string {
  const scope = settings.speedBonusCorrectOnly ? 'correct answers only' : 'any in-time answer';
  if (settings.speedBonusMode === 'dynamic') {
    return `+${settings.fastBonusPoints} full under ${settings.fastBonusSeconds}s, fading to 0 at ${settings.fastBonusZeroSeconds}s (${scope})`;
  }
  return `+${settings.fastBonusPoints} if under ${settings.fastBonusSeconds}s (${scope})`;
}

function roundBonus(value: number): number {
  return Math.round(value * 1000) / 1000;
}

function clampNumber(value: number, min: number, max: number, fallback: number): number {
  const numeric = Number(String(value).replace(',', '.'));
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, numeric));
}
