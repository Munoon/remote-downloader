export function buildTimeRemainingMessage(secondsRemaining: number): string {
  if (secondsRemaining < 60) {
    return `${secondsRemaining} seconds remaining`;
  } else if (secondsRemaining < 60 * 60) {
    return `${Math.floor(secondsRemaining / 60)} minutes remaining`;
  } else if (secondsRemaining < 24 * 60 * 60) {
    return `${Math.floor(secondsRemaining / 60 * 60)} hours remaining`;
  } else {
    return `${Math.floor(secondsRemaining / 24 * 60 * 60)} days remaining`;
  }
}

export function buildSpeedMessage(bytesPerSecond: number): string {
  if (bytesPerSecond < 1024) {
    return `${Math.floor(bytesPerSecond)} B/s`;
  } else if (bytesPerSecond < 1024 ** 2) {
    return `${Math.floor(bytesPerSecond / 1024)} KB/s`;
  } else if (bytesPerSecond < 1024 ** 3) {
    return `${Math.floor(bytesPerSecond / 1024 ** 2)} MB/s`;
  } else if (bytesPerSecond < 1024 ** 4) {
    return `${Math.floor(bytesPerSecond / 1024 ** 3)} GB/s`;
  } else if (bytesPerSecond < 1024 ** 5) {
    return `${Math.floor(bytesPerSecond / 1024 ** 4)} TB/s`;
  } else {
    return `${Math.floor(bytesPerSecond / 1024 ** 5)} PB/s`;
  }
}