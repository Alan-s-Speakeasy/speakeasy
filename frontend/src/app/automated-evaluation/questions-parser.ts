export interface EvaluationQuestion {
  index: number
  question: string
  expectedAnswers: string[]
}

/**
 * Parses an evaluation questions file.
 *
 * Preferred block format (supports multi-line SPARQL):
 *
 *   Q: Who directed Titanic?
 *   A: James Cameron
 *
 *   ---
 *   Q:
 *   SELECT ?movie WHERE {
 *     ?movie dbo:director dbr:James_Cameron .
 *   }
 *   A: Titanic
 *   A: Avatar
 *
 * JSON is also accepted: [{"question":"...","answers":["a","b"]}]
 * The older numbered list (questions, blank line, answers) still works.
 */
export function parseQuestionsFile(raw: string): EvaluationQuestion[] {
  const text = raw.replace(/^\uFEFF/, '').replace(/\r\n/g, '\n').trim()
  if (!text) {
    throw new Error('The questions file is empty.')
  }

  const parsed = text.startsWith('[') || text.startsWith('{')
    ? parseJsonQuestions(text)
    : looksLikeBlockFormat(text)
      ? parseBlockQuestions(text)
      : parseLegacyQuestions(text)

  if (parsed.length === 0) {
    throw new Error('No questions were found in the file.')
  }
  return parsed
}

export function expectedAnswersLabel(item: EvaluationQuestion): string {
  return item.expectedAnswers.join(' / ')
}

export type AnswerVerdict = 'correct' | 'partial' | 'incorrect'

export function answersMatch(expected: string | string[], actual: string): boolean {
  return scoreAnswer(expected, actual) === 'correct'
}

export function scoreAnswer(expected: string | string[], actual: string): AnswerVerdict {
  const candidates = Array.isArray(expected) ? expected : [expected]
  if (candidates.some(candidate => answersMatchOne(candidate, actual))) {
    return 'correct'
  }
  if (candidates.some(candidate => answersPartialOne(candidate, actual))) {
    return 'partial'
  }
  return 'incorrect'
}

function answersMatchOne(expected: string, actual: string): boolean {
  const expectedNorm = normalizeAnswer(expected)
  const actualNorm = normalizeAnswer(actual)
  if (!expectedNorm || !actualNorm) {
    return false
  }
  return actualNorm.includes(expectedNorm) || expectedNorm.includes(actualNorm)
}

function answersPartialOne(expected: string, actual: string): boolean {
  const expectedTokens = tokenizeAnswer(expected)
  const actualTokens = new Set(tokenizeAnswer(actual))
  if (expectedTokens.length === 0 || actualTokens.size === 0) {
    return false
  }
  const overlap = expectedTokens.filter(token => actualTokens.has(token)).length
  const needed = expectedTokens.length === 1 ? 1 : Math.ceil(expectedTokens.length / 2)
  return overlap >= needed && overlap < expectedTokens.length
}

function tokenizeAnswer(value: string): string[] {
  return normalizeAnswer(value)
    .split(' ')
    .map(token => token.trim())
    .filter(token => token.length > 0)
}

function normalizeAnswer(value: string): string {
  return value
    .toLowerCase()
    .replace(/[‘’]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

function looksLikeBlockFormat(text: string): boolean {
  return /^\s*---+\s*$/m.test(text) || /^\s*Q\s*:/m.test(text)
}

function parseBlockQuestions(text: string): EvaluationQuestion[] {
  return text
    .split(/^\s*---+\s*$/m)
    .map(record => parseBlockRecord(record))
    .filter((item): item is {question: string, expectedAnswers: string[]} => item != null)
    .map((item, index) => ({index, ...item}))
}

function parseBlockRecord(record: string): {question: string, expectedAnswers: string[]} | null {
  const lines = record.replace(/\r\n/g, '\n').split('\n')
  let current: 'q' | 'a' | null = null
  let question = ''
  let currentAnswer = ''
  const answers: string[] = []

  const flushAnswer = () => {
    if (current !== 'a') {
      return
    }
    splitAcceptedAnswers(currentAnswer).forEach(answer => answers.push(answer))
    currentAnswer = ''
  }

  for (const line of lines) {
    const trimmed = line.trim()
    if (current == null && (trimmed.length === 0 || trimmed.startsWith('#'))) {
      continue
    }

    const questionMatch = line.match(/^Q\s*:\s?(.*)$/i)
    if (questionMatch) {
      flushAnswer()
      current = 'q'
      question = appendLine(question, questionMatch[1])
      continue
    }

    const answerMatch = line.match(/^A\s*:\s?(.*)$/i)
    if (answerMatch) {
      flushAnswer()
      current = 'a'
      currentAnswer = answerMatch[1]
      continue
    }

    if (current === 'q') {
      question = appendLine(question, line)
    } else if (current === 'a') {
      currentAnswer = appendLine(currentAnswer, line)
    }
  }
  flushAnswer()

  const cleanQuestion = question.trim()
  if (!cleanQuestion || answers.length === 0) {
    return null
  }
  return {question: cleanQuestion, expectedAnswers: answers}
}

function parseJsonQuestions(raw: string): EvaluationQuestion[] {
  let data: unknown
  try {
    data = JSON.parse(raw)
  } catch {
    throw new Error('The questions JSON file is invalid.')
  }

  const rows = Array.isArray(data)
    ? data
    : (data && typeof data === 'object' && Array.isArray((data as {questions?: unknown}).questions))
      ? (data as {questions: unknown[]}).questions
      : null
  if (!rows) {
    throw new Error('JSON must be an array of {question, answers} objects.')
  }

  return rows.map((row, index) => {
    if (!row || typeof row !== 'object') {
      throw new Error(`Question ${index + 1} is not an object.`)
    }
    const item = row as Record<string, unknown>
    const question = String(item.question ?? item.q ?? '').trim()
    const answers = collectJsonAnswers(item)
    if (!question) {
      throw new Error(`Question ${index + 1} is missing a question.`)
    }
    if (answers.length === 0) {
      throw new Error(`Question ${index + 1} is missing accepted answers.`)
    }
    return {index, question, expectedAnswers: answers}
  })
}

function collectJsonAnswers(item: Record<string, unknown>): string[] {
  const lists = [item.answers, item.expectedAnswers, item.accepted]
  for (const list of lists) {
    if (Array.isArray(list)) {
      return list.map(value => String(value).trim()).filter(value => value.length > 0)
    }
  }
  const single = item.answer ?? item.expected ?? item.expectedAnswer
  if (typeof single === 'string' && single.trim()) {
    return splitAcceptedAnswers(single)
  }
  return []
}

function parseLegacyQuestions(raw: string): EvaluationQuestion[] {
  const blocks = raw.split(/\n\s*\n/)
  if (blocks.length < 2) {
    throw new Error('Use Q:/A: blocks separated by --- , or a numbered question list and answer list separated by a blank line.')
  }

  const questions = parseNumberedLines(blocks[0])
  const answers = parseNumberedLines(blocks.slice(1).join('\n'))
  if (questions.length === 0) {
    throw new Error('No questions were found in the file.')
  }
  if (questions.length !== answers.length) {
    throw new Error(`Found ${questions.length} questions but ${answers.length} answers.`)
  }

  return questions.map((question, index) => ({
    index,
    question,
    expectedAnswers: splitAcceptedAnswers(answers[index])
  }))
}

function parseNumberedLines(block: string): string[] {
  return block
    .split('\n')
    .map(line => line.trim())
    .filter(line => line.length > 0)
    .map(line => line.replace(/^\d+\.\s*/, '').trim())
    .filter(line => line.length > 0)
}

function splitAcceptedAnswers(value: string): string[] {
  const trimmed = value.trim()
  if (!trimmed) {
    return []
  }
  if (trimmed.includes('\n')) {
    return [trimmed]
  }
  return trimmed
    .split(/\s+\|\s+/)
    .map(part => part.trim())
    .filter(part => part.length > 0)
}

function appendLine(current: string, line: string): string {
  return current.length === 0 ? line : `${current}\n${line}`
}
