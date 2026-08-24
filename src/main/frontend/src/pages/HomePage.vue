<script setup>
import { ref } from 'vue'

const props = defineProps({
  initialState: { type: Object, required: true }
})

const count = ref(props.initialState.count)
const serverMessage = ref(null)

async function loadMessage() {
  const response = await fetch('/api/message')
  serverMessage.value = await response.json()
}
</script>

<template>
  <main>
    <h1>{{ initialState.greeting }}</h1>

    <ul>
      <li v-for="item in initialState.items" :key="item">{{ item }}</li>
    </ul>

    <p>
      <button @click="count++">Clicked {{ count }} times</button>
      (proves hydration — this works without any page reload)
    </p>

    <p>
      <button @click="loadMessage">Load message from server</button>
      <span v-if="serverMessage">
        → {{ serverMessage.message }} ({{ serverMessage.serverTime }})
      </span>
    </p>

    <nav><a href="/about">About this experiment →</a></nav>
  </main>
</template>
