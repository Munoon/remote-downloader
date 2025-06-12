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

export function buildSpeedMessage(bytesPerSecond: number): string | null {
  if (bytesPerSecond === 0) {
    return null;
  }

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

export function resolveFileNameFromURL(url: string) {
  const startIndex = url.lastIndexOf('/');
  if (startIndex === -1) {
    return url;
  }

  const endIndex = url.lastIndexOf('?');
  return endIndex === -1 || startIndex > endIndex
    ? url.substring(startIndex + 1)
    : url.substring(startIndex + 1, endIndex);
}

export function arrayEquals(arr1: string[], arr2: string[]) {
  if (arr1.length != arr2.length) {
    return false;
  }

  for (let i = 0; i < arr1.length; i++) {
    if (arr1[i] !== arr2[i]) {
      return false;
    }
  }
  return true;
}

export function copyAndReplace<T>(arr: T[], matcher: (el: T) => boolean, newElement: T) {
  const index = arr.findIndex(matcher);
  if (index === -1) {
    return arr;
  }

  const copy = [...arr];
  copy[index] = newElement;
  return copy;
}

export function deleteElement<T>(arr: T[], matcher: (el: T) => boolean) {
  const index = arr.findIndex(matcher);
  if (index === -1) {
    return arr;
  }
  
  const copy = [...arr];
  copy.splice(index, 1);
  return copy;
}

export function validateFileName(fileName: string, objectName: string): { valid: boolean, validationMessage: string } {
  if (fileName.length === 0) {
    return { valid: false, validationMessage: objectName + ' name is empty.' };
  }
  if (fileName.length > 255) {
    return { valid: false, validationMessage: objectName + ' name is too long (limit is 255 characters).' };
  }
  if (fileName.includes('/') || fileName.includes('\0')) {
    return { valid: false, validationMessage: objectName + " name cannot contain '/' character." }
  }

  return { valid: true, validationMessage: '' };
}