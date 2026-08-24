<script setup>
import { ref } from 'vue'

defineProps({
  initialState: { type: Object, required: true }
})

const revealed = ref(false)
</script>

<template>
  <main>
    <h1>{{ initialState.heading }}</h1>

    <ul>
      <li v-for="fact in initialState.facts" :key="fact">{{ fact }}</li>
    </ul>

    <p>
      <button @click="revealed = !revealed">
        {{ revealed ? 'Hide' : 'Show' }} details
      </button>
      (proves this page hydrates independently)
    </p>

    <p v-if="revealed">{{ initialState.details }}</p>

    <nav><a href="/">← Home</a></nav>
  </main>
</template>
