import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine,
} from 'recharts'
import type { PassRatePoint } from '@/lib/types'
import { format, parseISO } from 'date-fns'

interface Props {
  data: PassRatePoint[]
  threshold?: number
  height?: number
}

export default function PassRateChart({ data, threshold = 80, height = 220 }: Props) {
  const formatted = data.map(p => ({
    ...p,
    date: format(parseISO(p.date), 'MMM d'),
    passRate: Math.round(p.passRate * 10) / 10,
  }))

  return (
    <ResponsiveContainer width="100%" height={height}>
      <LineChart data={formatted} margin={{ top: 4, right: 8, bottom: 0, left: -16 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#f1f5f9" />
        <XAxis dataKey="date" tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} />
        <YAxis domain={[0, 100]} tick={{ fontSize: 11, fill: '#94a3b8' }} tickLine={false} unit="%" />
        <Tooltip
          formatter={(value: number) => [`${value}%`, 'Pass Rate']}
          contentStyle={{ fontSize: 12, borderRadius: 8, border: '1px solid #e2e8f0' }}
        />
        <ReferenceLine y={threshold} stroke="#f59e0b" strokeDasharray="4 4" label={{ value: `${threshold}%`, fontSize: 10, fill: '#f59e0b' }} />
        <Line
          type="monotone" dataKey="passRate" stroke="#3b82f6"
          strokeWidth={2} dot={false} activeDot={{ r: 4 }}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
