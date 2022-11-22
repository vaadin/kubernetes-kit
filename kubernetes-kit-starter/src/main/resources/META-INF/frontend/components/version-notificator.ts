import { css, html, LitElement } from 'lit';
import { customElement } from 'lit/decorators.js';

@customElement('version-notificator')
export class VersionNotificator extends LitElement {

    currentVersion?: string;
    updateVersion?: string;

    static get styles() {
        return css`
      :host {
        display: block;
        z-index: 99999999;
        position: absolute;
        max-width: calc(100% - 100px);
        min-height: 40px;
        top: 6px;
        right: 6px;
        padding: 14px 10px;
        border-radius: 4px;
        text-align: center;
        background: #f5e087e6;
        color: #42302B;
        box-sizing: border-box;
        box-shadow: inset 0 0 5px;
        cursor: move;
      }
      a {
        color: #0000ff96;
        cursor: pointer;
        font-weight: bold;
      }
      `;
    }

    render() {
        return html`
      Version <b>${this.updateVersion}</b> is available, please save your work and <a @click=${this.onClick}>click here</a>.
    `;
    }

    onClick() {
        this.dispatchEvent(new CustomEvent('load-version'));
    }

    firstUpdated(p: any) {
        super.firstUpdated(p);
        this.setAttribute('draggable', 'true');
        this.addEventListener('dragstart', this.dragstart);
        document.body.addEventListener('dragover', (e: Event) => this.dragover(e));
        document.body.addEventListener('dragend', (e: Event) => this.dragend(e));
        document.body.addEventListener('drop', (e: Event) => this.drop(e));
    }

    dragstart(e: Event) {
        const event = e as DragEvent & { target: HTMLElement, dataTransfer: DataTransfer};
        const style = window.getComputedStyle(event.target, null);
        const left = parseInt(style.getPropertyValue("left"), 10) - event.clientX;
        const top = parseInt(style.getPropertyValue("top"), 10) - event.clientY;
        event.dataTransfer.setData("text/plain", left + ',' + top);
        setTimeout(() => this.style.visibility = 'hidden', 0);
    }

    dragover(event: Event) {
        event.preventDefault();
    }

    drop(e: Event) {
        const event = e as DragEvent & { dataTransfer: DataTransfer};
        const data = event.dataTransfer.getData("text/plain").split(',');
        const left = event.clientX + parseInt(data[0], 10);
        const top = event.clientY + parseInt(data[1], 10);
        this.style.left = (left < 0 ? 0 : left) + 'px';
        this.style.top = (top < 0 ? 0 : top)  + 'px';
        this.style.right = 'auto';
        this.style.bottom = 'auto';
        this.style.minHeight = 'auto';
        event.preventDefault();
    }

    dragend(e: Event) {
        this.style.visibility = 'visible';
    }
}
