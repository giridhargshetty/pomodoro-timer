document.addEventListener('DOMContentLoaded', () => {
    const timerDisplay = document.getElementById('timer');
    const startBtn = document.getElementById('start-btn');
    const pauseBtn = document.getElementById('pause-btn');
    const resetBtn = document.getElementById('reset-btn');
    const applyBtn = document.getElementById('apply-btn');
    const statusDisplay = document.getElementById('status');

    let workTime = 25 * 60; // 25 minutes in seconds
    let shortBreak = 5 * 60; // 5 minutes in seconds
    let longBreak = 15 * 60; // 15 minutes in seconds
    let currentTime = workTime;
    let isRunning = false;
    let isWorking = true;
    let pomodorosCompleted = 0;
    let timerInterval;

    function updateDisplay() {
        const minutes = Math.floor(currentTime / 60);
        const seconds = currentTime % 60;
        timerDisplay.textContent = `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }

    function startTimer() {
        if (!isRunning) {
            isRunning = true;
            timerInterval = setInterval(() => {
                if (currentTime > 0) {
                    currentTime--;
                    updateDisplay();
                } else {
                    clearInterval(timerInterval);
                    isRunning = false;
                    playSound();
                    if (isWorking) {
                        pomodorosCompleted++;
                        if (pomodorosCompleted % 4 === 0) {
                            currentTime = longBreak;
                            statusDisplay.textContent = 'Long Break';
                            statusDisplay.className = 'status long-break';
                        } else {
                            currentTime = shortBreak;
                            statusDisplay.textContent = 'Short Break';
                            statusDisplay.className = 'status short-break';
                        }
                        isWorking = false;
                    } else {
                        currentTime = workTime;
                        statusDisplay.textContent = 'Work Time';
                        statusDisplay.className = 'status work-time';
                        isWorking = true;
                    }
                    updateDisplay();
                    alert(isWorking ? "Time's up! Take a break." : "Time's up! Back to work.");
                }
            }, 1000);
        }
    }

    function pauseTimer() {
        isRunning = false;
        clearInterval(timerInterval);
    }

    function resetTimer() {
        pauseTimer();
        if (isWorking) {
            currentTime = workTime;
        } else {
            if (pomodorosCompleted % 4 === 0) {
                currentTime = longBreak;
            } else {
                currentTime = shortBreak;
            }
        }
        updateDisplay();
    }

    function applySettings() {
        const workTimeInput = document.getElementById('work-time');
        const shortBreakInput = document.getElementById('short-break');
        const longBreakInput = document.getElementById('long-break');

        workTime = parseInt(workTimeInput.value) * 60;
        shortBreak = parseInt(shortBreakInput.value) * 60;
        longBreak = parseInt(longBreakInput.value) * 60;

        if (isWorking) {
            currentTime = workTime;
        } else {
            if (pomodorosCompleted % 4 === 0) {
                currentTime = longBreak;
            } else {
                currentTime = shortBreak;
            }
        }

        updateDisplay();
        alert('Settings applied!');
    }

    function playSound() {
        const audio = new Audio('https://assets.mixkit.co/sfx/preview/mixkit-classic-alarm-995.mp3');
        audio.play().catch(e => {
            console.log('Audio play failed:', e);
            // Request permission for audio playback
            document.addEventListener('click', function() {
                audio.play().catch(e => console.log('Audio play failed after click:', e));
            }, { once: true });
        });
    }

    startBtn.addEventListener('click', startTimer);
    pauseBtn.addEventListener('click', pauseTimer);
    resetBtn.addEventListener('click', resetTimer);
    applyBtn.addEventListener('click', applySettings);

    statusDisplay.className = 'status work-time';
    updateDisplay();
});