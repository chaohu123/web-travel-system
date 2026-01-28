<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoutePlanningStore } from '../../store/routePlanning'

const store = useRoutePlanningStore()
const editingName = ref(false)
const nameInput = ref('')

const displayName = computed(() => store.routeName)
const startDate = computed({
  get: () => store.startDate,
  set: (v: string) => { store.startDate = v },
})
const endDate = computed({
  get: () => store.endDate,
  set: (v: string) => { store.endDate = v },
})
const peopleCount = computed({
  get: () => store.peopleCount,
  set: (v: number) => { store.peopleCount = v },
})
const summary = computed(() => store.budgetSummary)

function startEditName() {
  nameInput.value = store.routeName
  editingName.value = true
}
function saveName() {
  const v = nameInput.value?.trim()
  if (v) store.setRouteName(v)
  editingName.value = false
}
</script>

<template>
  <div class="route-header rounded-2xl bg-white/95 border border-slate-200/80 shadow-sm p-4 sm:p-5">
    <div class="flex flex-wrap items-center gap-x-4 gap-y-3">
      <div class="flex-1 min-w-0">
        <template v-if="editingName">
          <input
            ref="nameInput"
            v-model="nameInput"
            type="text"
            class="route-name-input w-full text-lg font-semibold text-slate-800 border border-teal-300 rounded-lg px-3 py-2 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none"
            @blur="saveName"
            @keydown.enter="saveName"
          />
        </template>
        <button
          v-else
          type="button"
          class="route-name-btn text-left text-lg font-semibold text-slate-800 hover:text-teal-600 transition-colors truncate max-w-full"
          @click="startEditName"
        >
          {{ displayName }}
          <span class="inline-block ml-1 text-slate-400 text-sm font-normal">(点击编辑)</span>
        </button>
      </div>
      <div class="flex flex-wrap items-center gap-3 text-sm text-slate-600">
        <span>出发</span>
        <input
          v-model="startDate"
          type="date"
          class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-slate-800 focus:ring-2 focus:ring-teal-500/50 focus:border-teal-500"
        />
        <span>—</span>
        <span>结束</span>
        <input
          v-model="endDate"
          type="date"
          class="rounded-lg border border-slate-200 px-2.5 py-1.5 text-slate-800 focus:ring-2 focus:ring-teal-500/50 focus:border-teal-500"
        />
        <span class="text-slate-400">|</span>
        <span>同行</span>
        <input
          v-model.number="peopleCount"
          type="number"
          min="1"
          max="20"
          class="w-14 rounded-lg border border-slate-200 px-2 py-1.5 text-slate-800 text-center focus:ring-2 focus:ring-teal-500/50 focus:border-teal-500"
        />
        <span>人</span>
      </div>
    </div>
    <div class="mt-3 pt-3 border-t border-slate-100 flex flex-wrap gap-4 text-sm">
      <span class="text-slate-500">当前预算汇总：</span>
      <span class="font-medium text-slate-800">总计 ¥{{ summary.total.toLocaleString() }}</span>
      <span class="text-slate-500">人均 ¥{{ summary.perPerson.toLocaleString() }}</span>
      <span class="text-slate-500" v-if="summary.perDay">日均 ¥{{ summary.perDay.toLocaleString() }}</span>
    </div>
  </div>
</template>
