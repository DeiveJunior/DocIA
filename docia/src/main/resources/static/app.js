const form = document.querySelector('#analysis-form');
const fileInput = document.querySelector('#document');
const fileLabel = document.querySelector('#file-label');
const commandInput = document.querySelector('#command');
const submitButton = document.querySelector('#submit-button');
const formMessage = document.querySelector('#form-message');
const resultsSection = document.querySelector('#results');
const resultList = document.querySelector('#result-list');
const resultTitle = document.querySelector('#result-title');
const resultCount = document.querySelector('#result-count');
const resultMessage = document.querySelector('#result-message');
const uploadBox = document.querySelector('.upload-box');

const MAX_FILE_SIZE = 20 * 1024 * 1024;

fileInput.addEventListener('change', () => {
  const file = fileInput.files?.[0];
  fileLabel.textContent = file ? file.name : 'Escolha um arquivo ou arraste até aqui';
  clearMessage();
});

for (const eventName of ['dragenter', 'dragover']) {
  uploadBox.addEventListener(eventName, (event) => {
    event.preventDefault();
    uploadBox.classList.add('is-dragging');
  });
}

for (const eventName of ['dragleave', 'drop']) {
  uploadBox.addEventListener(eventName, (event) => {
    event.preventDefault();
    uploadBox.classList.remove('is-dragging');
  });
}

uploadBox.addEventListener('drop', (event) => {
  const [file] = event.dataTransfer?.files ?? [];
  if (!file) return;
  const transfer = new DataTransfer();
  transfer.items.add(file);
  fileInput.files = transfer.files;
  fileInput.dispatchEvent(new Event('change', { bubbles: true }));
});

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  clearMessage();
  resultsSection.hidden = true;

  const file = fileInput.files?.[0];
  const command = commandInput.value.trim();
  if (!file) {
    showMessage('Selecione um arquivo PDF para continuar.');
    fileInput.focus();
    return;
  }
  if (!file.name.toLowerCase().endsWith('.pdf')) {
    showMessage('O arquivo selecionado não é um PDF. Escolha um arquivo com extensão .pdf.');
    fileInput.focus();
    return;
  }
  if (file.size === 0) {
    showMessage('O arquivo PDF está vazio. Escolha outro documento.');
    fileInput.focus();
    return;
  }
  if (file.size > MAX_FILE_SIZE) {
    showMessage('O PDF excede o limite permitido de 20 MB.');
    fileInput.focus();
    return;
  }
  if (!command) {
    showMessage('Escreva o que deseja encontrar no documento.');
    commandInput.focus();
    return;
  }

  const payload = new FormData();
  payload.append('document', file);
  payload.append('command', command);
  setLoading(true);

  try {
    const response = await fetch('/api/documents/command', {
      method: 'POST',
      body: payload
    });
    const body = await readResponse(response);
    if (!response.ok) {
      throw new Error(errorMessage(body, response.status));
    }
    renderResults(body);
  } catch (error) {
    showMessage(error instanceof TypeError
      ? 'Não foi possível conectar à API. Verifique se o DocIA está disponível e tente novamente.'
      : (error.message || 'Ocorreu um erro ao analisar o documento. Tente novamente.'));
  } finally {
    setLoading(false);
  }
});

async function readResponse(response) {
  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function errorMessage(body, status) {
  const detail = body?.detail || body?.message || body?.title || body?.error;
  if (status === 413) return 'O arquivo excede o tamanho máximo permitido de 20 MB.';
  if (status === 400 && detail) return String(detail);
  if (status === 415) return 'O formato do arquivo não é suportado. Envie um PDF válido.';
  if (status >= 500) return 'Ocorreu um erro no servidor durante a análise. Tente novamente mais tarde.';
  return detail ? String(detail) : `Não foi possível analisar o documento (erro ${status}).`;
}

function renderResults(data) {
  resultList.replaceChildren();
  resultMessage.hidden = true;
  resultCount.textContent = '';
  resultTitle.textContent = 'Informações encontradas';
  resultsSection.hidden = false;

  if (String(data?.status || '').toUpperCase() === 'UNSUPPORTED') {
    resultTitle.textContent = 'Comando não suportado';
    showResultMessage(
      'Não foi possível identificar uma solicitação relacionada às informações que a DocIA extrai deste documento. Tente pedir CPF/CNPJ, endereço, telefone, e-mail ou o texto completo.',
      'error');
    resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    return;
  }

  if (data?.responseType === 'GENERAL_ANSWER') {
    renderGeneralAnswer(data);
  } else {
    const records = collectRecords(data);
    resultCount.textContent = `${records.length} ${records.length === 1 ? 'resultado' : 'resultados'}`;
    if (records.length === 0) {
      resultList.append(makeEmptyState('Nenhuma informação correspondente foi encontrada no documento.'));
    } else {
      for (const record of records) resultList.append(makeResultCard(record));
    }
  }
  resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function renderGeneralAnswer(data) {
  resultTitle.textContent = 'Resposta sobre o documento';

  const answerCard = document.createElement('article');
  answerCard.className = 'result-card';

  const label = document.createElement('p');
  label.className = 'result-type';
  label.textContent = data.answerStatus === 'AI_UNAVAILABLE'
    ? 'Interpretação indisponível'
    : data.answerStatus === 'NOT_FOUND'
      ? 'Informação não encontrada'
      : 'Resposta';

  const answer = document.createElement('p');
  answer.className = 'result-value';
  answer.textContent = data.answer || '';
  answerCard.append(label, answer);

  for (const source of data.sources ?? []) {
    if (Number.isInteger(source.pageNumber)) {
      const page = document.createElement('span');
      page.className = 'page-tag';
      page.textContent = `Página ${source.pageNumber}`;
      answerCard.append(page);
    }
  }
  resultList.append(answerCard);

  const records = collectRecords(data.deterministicResults);
  if (records.length > 0) {
    const heading = document.createElement('p');
    heading.className = 'result-type';
    heading.textContent = 'Resultados encontrados pelos extratores';
    resultList.append(heading);
    for (const record of records) {
      resultList.append(makeResultCard(record));
    }
  }

  const answerCount = data.answerStatus === 'ANSWERED' ? 1 : 0;
  const total = records.length + answerCount;
  resultCount.textContent = `${total} ${total === 1 ? 'resultado' : 'resultados'}`;
}

function collectRecords(data) {
  const records = [];
  for (const item of data?.documents ?? []) {
    records.push({ type: item.type || 'Documento', value: item.value, page: item.pageNumber });
  }
  for (const item of data?.names ?? []) {
    records.push({ type: 'Nome', value: item.name, page: item.pageNumber });
  }
  for (const item of data?.addresses ?? []) {
    const parts = [item.street, item.number, item.complement, item.neighborhood, item.city, item.state, item.zipCode]
      .filter((part) => part !== null && part !== undefined && String(part).trim() !== '');
    records.push({ type: 'Endereço', value: parts.join(', '), page: item.pageNumber });
  }
  for (const item of data?.phones ?? []) {
    records.push({ type: 'Telefone', value: item.phone, page: item.pageNumber });
  }
  for (const item of data?.emails ?? []) {
    records.push({ type: 'E-mail', value: item.email, page: item.pageNumber });
  }
  for (const item of data?.pages ?? []) {
    records.push({ type: `Texto — página ${item.pageNumber}`, value: item.text, page: item.pageNumber });
  }
  return records.filter((record) => record.value !== null && record.value !== undefined && String(record.value).trim() !== '');
}

function makeResultCard(record) {
  const card = document.createElement('article');
  card.className = 'result-card';
  const top = document.createElement('div');
  top.className = 'result-card-top';
  const body = document.createElement('div');
  const type = document.createElement('p');
  type.className = 'result-type';
  type.textContent = record.type;
  const value = document.createElement('p');
  value.className = 'result-value';
  value.textContent = record.value;
  body.append(type, value);
  top.append(body);
  if (Number.isInteger(record.page)) {
    const page = document.createElement('span');
    page.className = 'page-tag';
    page.textContent = `Página ${record.page}`;
    top.append(page);
  }
  card.append(top);
  return card;
}

function makeEmptyState(message) {
  const element = document.createElement('p');
  element.className = 'empty-results';
  element.textContent = message;
  return element;
}

function showMessage(message) {
  formMessage.textContent = message;
  formMessage.hidden = false;
  formMessage.className = 'message error';
}

function showResultMessage(message, type) {
  resultMessage.textContent = message;
  resultMessage.hidden = false;
  resultMessage.className = `message result-message ${type}`;
}

function clearMessage() {
  formMessage.textContent = '';
  formMessage.hidden = true;
}

function setLoading(loading) {
  submitButton.disabled = loading;
  submitButton.classList.toggle('is-loading', loading);
  submitButton.querySelector('.button-label').textContent = loading ? 'Analisando documento...' : 'Analisar documento';
  submitButton.setAttribute('aria-busy', String(loading));
}
