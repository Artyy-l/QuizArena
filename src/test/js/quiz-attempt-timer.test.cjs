const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const template = fs.readFileSync(path.join(__dirname, '../../main/resources/templates/quiz-attempt.html'), 'utf8');
const script = template.match(/<script th:inline="javascript">([\s\S]*?)<\/script>/)?.[1];
assert.ok(script, 'quiz attempt script is present');

function makePage(questionType, selectedIds, stakeValue = '0') {
    const requests = [];
    const storage = new Map();
    const inputs = selectedIds.map(id => ({ value: String(id), checked: true }));
    const form = {
        dataset: { questionType },
        querySelectorAll: () => inputs
    };
    const elements = {
        answerForm: form,
        attemptIdInput: { value: '11' },
        currentQuestionId: { value: '22' },
        submitButton: { disabled: false },
        timeRemaining: { dataset: { defaultTime: '60' } },
        catBetInput: { value: stakeValue },
        catBetConfirm: { disabled: false },
        catCurrentScoreDisplay: { textContent: '10' },
        catStakeTimerText: { textContent: '' },
        catInBagOverlay: { classList: { contains: () => false } }
    };
    const context = vm.createContext({
        console,
        Date,
        FormData: class { get() { return inputs[0]?.value ?? null; } },
        document: { getElementById: id => elements[id] ?? null },
        window: { addEventListener() {}, location: { pathname: '/quiz/attempt/11/question' } },
        localStorage: { getItem: () => 'test-token' },
        sessionStorage: {
            getItem: key => storage.get(key) ?? null,
            setItem: (key, value) => storage.set(key, value),
            removeItem: key => storage.delete(key)
        },
        fetch: async (url, options) => {
            requests.push({ url, body: JSON.parse(options.body) });
            return {
                ok: true,
                json: async () => url.endsWith('/stake') ? { id: 22 } : { nextQuestion: null }
            };
        },
        setInterval: () => 1,
        clearInterval() {},
        alert: message => { throw new Error(message); }
    });
    vm.runInContext(script, context);
    context.showAnswerResult = () => {};
    context.updateQuestionOnPage = () => {};
    context.resetTimer = () => {};
    return { context, elements, requests, storage };
}

async function main() {
    for (const type of ['SINGLE_CHOICE', 'HUNDRED_TO_ONE', 'TRUE_FALSE', 'TEXT']) {
        const single = makePage(type, [7]);
        await single.context.submitAnswer(null, null);
        assert.equal(single.requests[0].body.selectedAnswerId, 7, type);
    }

    const multiple = makePage('MULTIPLE_CHOICE', [7, 8]);
    await multiple.context.submitAnswer(null, null);
    assert.deepEqual(Array.from(multiple.requests[0].body.selectedAnswerIds), [7, 8]);

    const empty = makePage('SINGLE_CHOICE', []);
    await empty.context.submitAnswer(null, null);
    assert.equal(empty.requests[0].body.selectedAnswerId, null);

    const stake = makePage('SINGLE_CHOICE', [], '7');
    stake.storage.set('catStakeDeadlineAt_11_22', String(Date.now() - 1000));
    stake.context.startStakeTimer60s();
    await new Promise(setImmediate);
    assert.equal(stake.requests.length, 1);
    assert.equal(stake.requests[0].body.stake, 7);
    assert.equal(stake.storage.size, 0);

    const race = makePage('SINGLE_CHOICE', [], '4');
    race.storage.set('catStakeDeadlineAt_11_22', String(Date.now() - 1000));
    const manualSubmission = race.context.submitCatStake();
    race.context.startStakeTimer60s();
    await manualSubmission;
    assert.equal(race.requests.length, 1, 'manual and timer stake submission must not race');

    console.log('Quiz timeout and stake auto-submit tests passed');
}

main().catch(error => { console.error(error); process.exitCode = 1; });
