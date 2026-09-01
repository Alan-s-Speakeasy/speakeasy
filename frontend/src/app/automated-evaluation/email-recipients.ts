export interface EmailRecipientMap {
  [username: string]: string[];
}

export interface ParsedEmailRecipients {
  mapping: EmailRecipientMap;
  invalidEmails: string[];
}

const EMAIL_PATTERN = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/;

export function isValidEmail(value: string): boolean {
  return EMAIL_PATTERN.test(value);
}

/**
 * Parses a recipient mapping file:
 *
 *   bot1: student1@uzh.ch, student2@uzh.ch
 *   bot2: student3@uzh.ch
 *
 * A space after the username also works: `murat a@gmail.com, b@gmail.com`
 */
export function parseEmailRecipients(raw: string): ParsedEmailRecipients {
  const mapping: EmailRecipientMap = {};
  const invalidEmails: string[] = [];

  raw
    .replace(/^\uFEFF/, '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('#'))
    .forEach((line) => {
      const separator = line.includes(':')
        ? line.indexOf(':')
        : line.search(/\s+/);
      if (separator <= 0) {
        return;
      }
      const username = line.slice(0, separator).trim();
      const emails = line
        .slice(separator + 1)
        .split(/[,;]+/)
        .map((part) => part.trim())
        .filter((part) => part.length > 0);
      if (!username || emails.length === 0) {
        return;
      }
      emails.forEach((email) => {
        if (!isValidEmail(email)) {
          invalidEmails.push(email);
          return;
        }
        const current = mapping[username] || [];
        if (
          !current.some(
            (existing) => existing.toLowerCase() === email.toLowerCase(),
          )
        ) {
          current.push(email);
        }
        mapping[username] = current;
      });
    });

  if (Object.keys(mapping).length === 0) {
    throw new Error('No username: email pairs were found in the file.');
  }

  return { mapping, invalidEmails: unique(invalidEmails) };
}

export function recipientsFor(
  mapping: EmailRecipientMap,
  username: string,
): string[] {
  const direct = mapping[username];
  if (direct && direct.length > 0) {
    return direct;
  }
  const match = Object.keys(mapping).find(
    (key) => key.toLowerCase() === username.toLowerCase(),
  );
  return match ? mapping[match] : [];
}

function unique(values: string[]): string[] {
  return [...new Set(values)];
}
