import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import FileContentViewer from '../src/components/shared/FileContentViewer.vue'

vi.mock('@/api/competition', () => ({
  downloadFileBlob: vi.fn(async () => new Blob(['%PDF-1.4'], { type: 'application/pdf' })),
}))

const createObjectURL = URL.createObjectURL
const revokeObjectURL = URL.revokeObjectURL

afterEach(() => {
  URL.createObjectURL = createObjectURL
  URL.revokeObjectURL = revokeObjectURL
})

describe('file preview lifecycle', () => {
  it('offers a download and releases the temporary URL when closed', async () => {
    const release = vi.fn()
    URL.createObjectURL = vi.fn(() => 'blob:preview-1')
    URL.revokeObjectURL = release

    const wrapper = mount(FileContentViewer, {
      props: { fileId: 1, fileName: '通知.pdf', visible: true },
      global: {
        directives: { loading: {} },
        stubs: {
          'el-dialog': { props: ['modelValue'], template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>' },
          'el-button': { template: '<button><slot /></button>' },
          'el-alert': true,
          'el-empty': true,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('iframe').attributes('src')).toBe('blob:preview-1')
    expect(wrapper.find('a[download="通知.pdf"]').attributes('href')).toBe('blob:preview-1')
    await wrapper.setProps({ visible: false })
    expect(release).toHaveBeenCalledWith('blob:preview-1')
    wrapper.unmount()
  })
})
